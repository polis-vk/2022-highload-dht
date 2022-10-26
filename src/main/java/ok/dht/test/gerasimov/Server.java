package ok.dht.test.gerasimov;

import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.Shard;
import ok.dht.test.gerasimov.sharding.VNode;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.pool.PoolException;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Server extends HttpServer {
    private static final String ENTITY_ENDPOINT = "/v0/entity";
    private static final String ADMIN_ENDPOINT = "/v0/admin";
    private static final String TIMESTAMP_HEADER = "Timestamp";
    private static final String TOMBSTONE_HEADER = "Tombstone";
    private static final Set<Integer> ENTITY_ALLOWED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorService executorService;
    private final ConsistentHash<String> consistentHash;
    private final ServiceImpl service;

    public Server(
            HttpServerConfig httpServerConfig,
            ServiceImpl service,
            ConsistentHash<String> consistentHash,
            ExecutorService executorService,
            ScheduledExecutorService scheduledExecutorService
    ) throws IOException {
        super(httpServerConfig);
        this.scheduledExecutorService = scheduledExecutorService;
        this.executorService = executorService;
        this.consistentHash = consistentHash;
        this.service = service;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.socket().close();
                session.close();
            }
        }
        super.stop();
        consistentHash.getShards().forEach(s -> s.getHttpClient().close());
        executorService.shutdown();
        scheduledExecutorService.shutdown();

        try {
            if (executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(() -> {
                try {
                    session.sendResponse(handleRequest(request));
                } catch (IOException e) {
                    throw new ServerException("Handler can not handle request", e);
                }
            });
        } catch (RejectedExecutionException e) {
            session.sendResponse(ResponseEntity.serviceUnavailable());
        }
    }

    private Response handleRequest(Request request) {
        try {
            return switch (request.getPath().toLowerCase()) {
                case ENTITY_ENDPOINT -> handleEntityRequest(request);
                case ADMIN_ENDPOINT -> handleAdminRequest(request);
                default -> ResponseEntity.badRequest("Unsupported path");
            };
        } catch (Exception e) {
            return ResponseEntity.serviceUnavailable(Arrays.toString(e.getStackTrace()));
        }
    }

    private Response handleEntityRequest(Request request) {
        if (!ENTITY_ALLOWED_METHODS.contains(request.getMethod())) {
            return ResponseEntity.methodNotAllowed();
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            return ResponseEntity.badRequest("The required <id> parameter was not passed");
        }

        String ack = request.getParameter("ack=");
        String from = request.getParameter("from=");

        EntityParameters entityParameters;
        if (ack == null && from == null) {
            int clusterSize = consistentHash.getShards().size();
            entityParameters = new EntityParameters(id, clusterSize / 2 + 1, clusterSize);
        } else if (ack != null && from != null) {
            try {
                int ackInt = Integer.parseInt(ack);
                int fromInt = Integer.parseInt(from);

                if (ackInt <= fromInt && fromInt <= consistentHash.getShards().size() && ackInt != 0) {
                    entityParameters = new EntityParameters(id, ackInt, fromInt);
                } else {
                    return ResponseEntity.badRequest("Invalid parameter replicas: ack and from violate conditions");
                }

            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest("Invalid parameter replicas: ack and from must be numbers");
            }
        } else {
            return ResponseEntity.badRequest("Invalid parameter replicas");
        }

        if (request.getHeader(TIMESTAMP_HEADER) != null) {
            entityParameters.setTimestamp(Long.valueOf(request.getHeader(TIMESTAMP_HEADER)));
            return getResponse(request, entityParameters);
        }

        request.addHeader(TIMESTAMP_HEADER + System.currentTimeMillis());
        entityParameters.setTimestamp(Long.valueOf(request.getHeader(TIMESTAMP_HEADER)));

        Shard firstShard = consistentHash.getShardByKey(id);
        List<Shard> shards = consistentHash.getShards(firstShard, entityParameters.getFrom());
        List<Response> responses = new ArrayList<>();

        for (Shard shard : shards) {
            if (shard.getPort() != port) {
                responses.add(circuitBreaker(shard, request));
                continue;
            }

            responses.add(getResponse(request, entityParameters));
        }

        return makeDecisionEntityRequest(responses, entityParameters, request);
    }

    private Response makeDecisionEntityRequest(
            List<Response> responses,
            EntityParameters entityParameters,
            Request request
    ) {
        List<Response> responsesWithStatus2xx = new ArrayList<>();
        for (Response response : responses) {
            int responseStatus = response.getStatus();

            if (responseStatus == 503 || responseStatus == 504) {
                continue;
            }

            responsesWithStatus2xx.add(response);
        }

        if (responsesWithStatus2xx.size() < entityParameters.getAck()) {
            return ResponseEntity.gatewayTimeout("504 Not Enough Replicas");
        }

        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                try {
                    DaoEntry recordToReturn = null;
                    for (Response response : responsesWithStatus2xx) {
                        DaoEntry recordFromResponse;

                        if (response.getHeader(TOMBSTONE_HEADER) != null || response.getStatus() == 404) {
                            if (response.getHeader(TIMESTAMP_HEADER) != null) {
                                recordFromResponse = new DaoEntry(
                                        Long.valueOf(response.getHeader(TIMESTAMP_HEADER)),
                                        null,
                                        true
                                );
                            } else {
                                recordFromResponse = new DaoEntry(0L, null, true);
                            }
                        } else {
                            recordFromResponse = ObjectMapper.deserialize(response.getBody());
                        }

                        recordToReturn = recordToReturn == null || recordFromResponse.compareTo(recordToReturn) > 0
                                ? recordFromResponse
                                : recordToReturn;
                    }

                    return recordToReturn == null
                            ? ResponseEntity.gatewayTimeout("504 Not Enough Replicas")
                            : getFinalResponseForGet(recordToReturn);
                } catch (IOException | ClassNotFoundException e) {
                    throw new ServerException("Exception during deserialize object", e);
                }
            }
            case Request.METHOD_PUT -> {
                return ResponseEntity.created();
            }
            case Request.METHOD_DELETE -> {
                return ResponseEntity.accepted();
            }
            default -> throw new IllegalStateException("Unexpected value: " + request.getMethod());
        }
    }

    private Response getFinalResponseForGet(DaoEntry daoEntry) {
        Response finalResponse;
        if (daoEntry.isTombstone()) {
            finalResponse = new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            finalResponse = new Response(Response.OK, daoEntry.getValue());
        }

        return finalResponse;
    }

    private Response handleAdminRequest(Request request) {
        if (Request.METHOD_GET != request.getMethod()) {
            return ResponseEntity.methodNotAllowed();
        }

        if (request.getHeader("From-Main") != null) {
            return service.handleAdminRequest();
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(service.handleAdminRequest().getBodyUtf8());
        stringBuilder.append("\n");
        request.addHeader("From-Main");
        for (Shard node : consistentHash.getShards()) {
            if (node.getPort() != port) {
                stringBuilder.append(circuitBreaker(node, request).getBodyUtf8());
                stringBuilder.append("\n");
            }
        }
        stringBuilder.append("---------------\n");

        for (VNode vnode : consistentHash.getVnodes()) {
            stringBuilder.append(vnode.toString());
            stringBuilder.append("\n");
        }

        return ResponseEntity.ok(stringBuilder.toString());
    }

    private Response getResponse(Request request, EntityParameters entityParameters) {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> service.handleGetRequest(entityParameters);
            case Request.METHOD_PUT -> service.handlePutRequest(entityParameters, request);
            case Request.METHOD_DELETE -> service.handleDeleteRequest(entityParameters);
            default -> throw new IllegalStateException("Unexpected value: " + request.getMethod());
        };
    }

    private Response circuitBreaker(Shard shard, Request request) {
        if (shard.isAvailable().get()) {
            try {
                return shard.getHttpClient().invoke(request);
            } catch (PoolException | InterruptedException | IOException | HttpException e) {
                if (shard.isAvailable().compareAndSet(true, false)) {
                    scheduledExecutorService.scheduleAtFixedRate(
                            createMonitoringNodeTask(shard),
                            5,
                            5,
                            TimeUnit.NANOSECONDS
                    );
                }
            }
        }
        return ResponseEntity.serviceUnavailable();
    }

    private Runnable createMonitoringNodeTask(Shard shard) {
        return () -> {
            try {
                shard.getHttpClient().connect(
                        String.format("%s:%s", shard.getHost(), shard.getPort())
                );
                shard.isAvailable().compareAndSet(false, true);
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // ignored
            }
        };
    }
}
