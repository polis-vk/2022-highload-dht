package ok.dht.test.monakhov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.monakhov.hashing.JumpingNodesRouter;
import ok.dht.test.monakhov.hashing.NodesRouter;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.pool.PoolException;
import one.nio.util.Utf8;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static ok.dht.test.monakhov.ServiceUtils.NOT_ENOUGH_REPLICAS;
import static ok.dht.test.monakhov.ServiceUtils.TIMESTAMP_HEADER;
import static ok.dht.test.monakhov.ServiceUtils.createConfigFromPort;
import static ok.dht.test.monakhov.ServiceUtils.isInvalidReplica;
import static ok.dht.test.monakhov.ServiceUtils.max;
import static one.nio.serial.Serializer.deserialize;
import static one.nio.serial.Serializer.serialize;

public class DbService implements Service {
    private static final Log log = LogFactory.getLog(DbService.class);
    private final ServiceConfig serviceConfig;
    private final NodesRouter nodesRouter;
    private ScheduledThreadPoolExecutor monitoringExecutor;
    private ExecutorService connectionExecutor;
    private HashMap<String, AsyncHttpClient> nodeClients;
    private RocksDB dao;
    private HttpServer server;

    public DbService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        nodesRouter = new JumpingNodesRouter(serviceConfig.clusterUrls());
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new AsyncHttpServer(createConfigFromPort(serviceConfig));
        try {
            dao = RocksDB.open(serviceConfig.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        nodeClients = new HashMap<>();
        for (String url : serviceConfig.clusterUrls()) {
            nodeClients.put(url, new AsyncHttpClient(new ConnectionString(url)));
        }

        monitoringExecutor = new ScheduledThreadPoolExecutor(
            Math.max(1, serviceConfig.clusterUrls().size() / 10)
        );

        connectionExecutor = Executors.newCachedThreadPool();

        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server == null || dao == null || nodeClients == null || monitoringExecutor == null) {
            return CompletableFuture.completedFuture(null);
        }
        server.stop();
        try {
            dao.syncWal();
            dao.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        for (var entry : nodeClients.entrySet()) {
            entry.getValue().close();
        }
        ExecutorUtils.shutdownGracefully(monitoringExecutor, log);
        ExecutorUtils.shutdownGracefully(connectionExecutor, log);
        server = null;
        dao = null;
        nodeClients = null;
        monitoringExecutor = null;
        connectionExecutor = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response manageRequest(
        @Param(value = "id") String id, @Param(value = "from") String fromParam,
        @Param(value = "ack") String ackParam, Request request
    ) {
        if (id == null || id.isBlank() || isInvalidReplica(ackParam, fromParam)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        int clusterSize = serviceConfig.clusterUrls().size();
        int ack = clusterSize;
        int from = clusterSize;

        if (ackParam != null) {
            ack = Integer.parseInt(ackParam);
            from = Integer.parseInt(fromParam);

            if (from > serviceConfig.clusterUrls().size() || ack > from || ack <= 0) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
        }

        String timestamp = request.getHeader(TIMESTAMP_HEADER);

        if (timestamp == null) {
            String[] nodeUrls = nodesRouter.getNodeUrls(id, from);
            List<Future<Response>> responses = multicast(request, id, nodeUrls);

            return createUserResponse(request, responses, ack);
        }

        return executeDaoOperation(id, request, Timestamp.valueOf(timestamp));
    }


    private List<Future<Response>> multicast(Request request, String id, String[] nodeUrls) {
        List<Future<Response>> responses = new ArrayList<>();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        for (final String nodeUrl : nodeUrls) {
            if (nodeUrl.equals(serviceConfig.selfUrl())) {
                responses.add(connectionExecutor.submit(() -> executeDaoOperation(id, request, timestamp)));
            } else {
                final var client = nodeClients.get(nodeUrl);

                // if (client.available().get()) {
                request.addHeader(TIMESTAMP_HEADER + timestamp);

                responses.add(connectionExecutor.submit(() -> {
                    try {
                        return client.invoke(request);
                    } catch (PoolException | SocketTimeoutException e) {
                        log.debug("Timeout connection to node:" + nodeUrl, e);
                        createMonitoringTask(nodeUrl, client);
                        throw e;
                    }
                }));
                // } else {
                //     log.debug("Node: " + nodeUrl + " is unavailable. Reconnection attempt skipped");
                //     responses.add(connectionExecutor.submit(() ->
                //         new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY))
                //     );
                // }
            }
        }
        return responses;
    }

    public Response createUserResponse(Request request, List<Future<Response>> futures, int ack) {
        List<Response> responses = new ArrayList<>();
        for (Future<Response> future : futures) {
            try {
                responses.add(future.get());
            } catch (InterruptedException e) {
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            } catch (ExecutionException e) {
                log.error("Exception occurred while redirecting request to another node", e);
            }
        }

        switch (request.getMethod()) {
        case Request.METHOD_GET -> {
            List<Response> successful = responses.stream().filter(r -> r.getStatus() == HTTP_OK).toList();

            if (successful.size() >= ack) {
                try {
                    EntryWrapper mostRecent = (EntryWrapper) deserialize(successful.get(0).getBody());

                    for (Response response : successful) {
                        EntryWrapper entry = (EntryWrapper) deserialize(response.getBody());
                        mostRecent = max(mostRecent, entry);
                    }

                    if (mostRecent.isTombstone) {
                        return new Response(Response.NOT_FOUND, Response.EMPTY);
                    }
                    return new Response(Response.OK, mostRecent.bytes);
                } catch (IOException | ClassNotFoundException e) {
                    log.error("Exception occurred while deserialization", e);
                }
            }

            if (responses.stream().filter(r -> r.getStatus() != HTTP_OK).count() >= ack) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
        }
        case Request.METHOD_PUT -> {
            if (responses.stream().filter(r -> r.getStatus() == HTTP_CREATED).count() >= ack) {
                return new Response(Response.CREATED, Response.EMPTY);
            }
        }
        case Request.METHOD_DELETE -> {
            if (responses.stream().filter(r -> r.getStatus() == HTTP_ACCEPTED).count() >= ack) {
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
        }
        default -> {
            return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }
        }

        return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
    }

    private Response executeDaoOperation(String id, Request request, Timestamp timestamp) {
        try {
            EntryWrapper entry = new EntryWrapper(request, timestamp);
            return executeDaoOperation(id, request, serialize(entry));
        } catch (IOException e) {
            log.error("Exception occurred while serialization", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response executeDaoOperation(String id, Request request, byte[] body) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> getEntity(id);
                case Request.METHOD_PUT -> putEntity(id, body);
                case Request.METHOD_DELETE -> deleteEntity(id, body);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (RocksDBException e) {
            log.error("Exception occurred in database interaction", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response getEntity(String id) throws RocksDBException {
        final var entry = dao.get(Utf8.toBytes(id));

        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }

        return new Response(Response.OK, entry);
    }

    public Response putEntity(String id, byte[] body) throws RocksDBException {
        dao.put(Utf8.toBytes(id), body);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(String id, byte[] body) throws RocksDBException {
        dao.put(Utf8.toBytes(id), body);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private void createMonitoringTask(String nodeUrl, AsyncHttpClient client) {
        if (client.available().compareAndSet(true, false)) {
            Runnable monitoringTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        client.connect(nodeUrl);
                        client.available().compareAndSet(false, true);
                    } catch (InterruptedException | PoolException | IOException | HttpException ex) {
                        log.debug("Impossible to establish connection to node: " + nodeUrl, ex);
                        monitoringExecutor.schedule(this, 5L, TimeUnit.SECONDS);
                    }
                }
            };
            monitoringExecutor.schedule(monitoringTask, 0L, TimeUnit.SECONDS);
        }
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DbService(config);
        }
    }
}
