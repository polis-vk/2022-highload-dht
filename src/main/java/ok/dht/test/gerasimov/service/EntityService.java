package ok.dht.test.gerasimov.service;

import ok.dht.test.gerasimov.model.DaoEntry;
import ok.dht.test.gerasimov.utils.ObjectMapper;
import ok.dht.test.gerasimov.replication.ReplicationEntityHandler;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import ok.dht.test.gerasimov.client.CircuitBreakerClient;
import ok.dht.test.gerasimov.exception.EntityServiceException;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.Shard;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.iq80.leveldb.DB;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class EntityService implements HandleService {
    private static final Set<Integer> ALLOWED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );
    private static final Map<Integer, String> ONE_NIO_METHOD_CODE_TO_HTTP_METHOD = Map.of(
            1, "GET",
            2, "POST",
            3, "HEAD",
            4, "OPTIONS",
            5, "PUT",
            6, "DELETE",
            7, "TRACE",
            8, "CONNECT",
            9, "PATCH"
    );

    private static final String ENDPOINT = "/v0/entity";
    private static final String TIMESTAMP_HEADER = "Timestamp";
    private static final String TOMBSTONE_HEADER = "Tombstone";

    private final ConsistentHash<String> consistentHash;
    private final DB dao;
    private final int port;
    private final CircuitBreakerClient client;

    public EntityService(DB dao, ConsistentHash<String> consistentHash, int port, CircuitBreakerClient client) {
        this.dao = dao;
        this.consistentHash = consistentHash;
        this.port = port;
        this.client = client;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        Parameters parameters = isValidRequest(request, session);

        if (parameters == null) {
            return;
        }

        try {
            if (!parameters.isMaster) {
                session.sendResponse(handle(parameters, request.getBody()));
                return;
            }
        } catch (IOException e) {
            throw new EntityServiceException("Slave server can not send response", e);
        }


        Shard firstShard = consistentHash.getShardByKey(parameters.id);
        List<Shard> shards = consistentHash.getShards(firstShard, parameters.from);


        ReplicationEntityHandler handler =
                new ReplicationEntityHandler(session, parameters.method, parameters.ack, parameters.from);

        for (Shard shard : shards) {
            if (shard.getPort() != port) {
                client.circuitBreaker(createHttpRequest(shard, parameters), shard)
                        .whenComplete(
                                (httpResponse, throwable) -> {
                                    if (httpResponse == null || throwable != null) {
                                        handler.handleResponse(42, null);
                                        return;
                                    }
                                    handler.handleResponse(httpResponse.statusCode(), httpResponse.body());
                                }
                        );
            } else {
                CompletableFuture.runAsync(
                        () -> {
                            Response response = handle(parameters, request.getBody());
                            handler.handleResponse(response.getStatus(), response.getBody());
                        }
                );
            }
        }


//        return makeDecisionEntityRequest(responses, ack, request);
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;
    }

    private Response handle(Parameters parameters, byte[] body) {
        byte[] id = parameters.id.getBytes(StandardCharsets.UTF_8);

        return switch (parameters.method) {
            case Request.METHOD_GET -> handleGetRequest(id);
            case Request.METHOD_PUT -> handlePutRequest(
                    id,
                    parameters.timestamp,
                    body
            );
            case Request.METHOD_DELETE -> handleDeleteRequest(
                    id,
                    parameters.timestamp
            );
            default -> ResponseEntity.methodNotAllowed();
        };
    }

    private Response handleGetRequest(byte[] id) {
        byte[] entry = dao.get(id);
        if (entry == null) {
            return ResponseEntity.notFound();
        }

        try {
            DaoEntry daoEntry = ObjectMapper.deserialize(entry);
            if (daoEntry.isTombstone()) {
                Response response = ResponseEntity.ok(entry);
                response.addHeader(TIMESTAMP_HEADER + ":" + daoEntry.getTimestamp());
                response.addHeader(TOMBSTONE_HEADER + ":" + daoEntry.isTombstone());
                return response;
            }

            Response response = ResponseEntity.ok(entry);
            response.addHeader(TIMESTAMP_HEADER + daoEntry.getTimestamp());
            return response;
        } catch (IOException | ClassNotFoundException e) {
            throw new EntityServiceException("Can not deserialize entry", e);
        }
    }

    private Response handlePutRequest(byte[] id, long timestamp, byte[] body) {
        try {
            dao.put(id,
                    ObjectMapper.serialize(
                            new DaoEntry(
                                    timestamp,
                                    body
                            )
                    )
            );
            return ResponseEntity.created();
        } catch (IOException e) {
            throw new EntityServiceException("Can not serialize request body", e);
        }
    }

    private Response handleDeleteRequest(byte[] id, long timestamp) {
        try {
            dao.put(id,
                    ObjectMapper.serialize(
                            new DaoEntry(
                                    timestamp,
                                    null,
                                    true
                            )
                    )
            );
            return ResponseEntity.accepted();
        } catch (IOException e) {
            throw new EntityServiceException("Can not serialize request body", e);
        }
    }

    private static HttpRequest createHttpRequest(Shard shard, Parameters parameters) {
        URI uri = URI.create(
                String.format("%s:%d%s?id=%s", shard.getHost(), shard.getPort(), ENDPOINT, parameters.id)
        );

        return HttpRequest.newBuilder()
                .uri(uri)
                .header(TIMESTAMP_HEADER, String.valueOf(parameters.timestamp))
                .method(
                        ONE_NIO_METHOD_CODE_TO_HTTP_METHOD.get(parameters.method),
                        HttpRequest.BodyPublishers.ofByteArray(parameters.body)
                )
                .build();
    }

    private Parameters isValidRequest(Request request, HttpSession session) {
        try {
            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(ResponseEntity.methodNotAllowed());
                return null;
            }

            String id = request.getParameter("id=");
            if (id == null || id.isEmpty()) {
                session.sendResponse(ResponseEntity.badRequest("The required <id> parameter was not passed"));
                return null;
            }

            String ackString = request.getParameter("ack=");
            String fromString = request.getParameter("from=");
            int ack;
            int from;

            if (ackString == null && fromString == null) {
                int clusterSize = consistentHash.getShards().size();
                ack = clusterSize / 2 + 1;
                from = clusterSize;
            } else if (ackString != null && fromString != null) {
                try {
                    ack = Integer.parseInt(ackString);
                    from = Integer.parseInt(fromString);

                    if (ack > from || from > consistentHash.getShards().size() || ack == 0) {
                        session.sendResponse(ResponseEntity.badRequest("Invalid parameter replicas: ack and from violate conditions"));
                        return null;
                    }

                } catch (NumberFormatException e) {
                    session.sendResponse(ResponseEntity.badRequest("Invalid parameter replicas: ack and from must be numbers"));
                    return null;
                }
            } else {
                session.sendResponse(ResponseEntity.badRequest("Invalid parameter replicas"));
                return null;
            }

            String timestampString = request.getHeader(TIMESTAMP_HEADER + ":");

            if (timestampString == null) {
                long currentTimeMillis = System.currentTimeMillis();
                request.addHeader(TIMESTAMP_HEADER + currentTimeMillis);
                return new Parameters(
                        id,
                        currentTimeMillis,
                        request.getMethod(),
                        true,
                        ack,
                        from,
                        request.getBody()
                );
            }

            try {
                return new Parameters(
                        id,
                        Long.parseLong(timestampString),
                        request.getMethod(),
                        false,
                        ack,
                        from,
                        request.getBody()
                );
            } catch (NumberFormatException e) {
                session.sendResponse(ResponseEntity.internalError("Invalid timestamp header"));
                return null;
            }
        } catch (IOException e) {
            throw new EntityServiceException("Can not send response", e);
        }
    }

    private static class Parameters {
        private final String id;
        private final long timestamp;
        private final int method;
        private final boolean isMaster;
        private final int ack;
        private final int from;
        private final byte[] body;

        public Parameters(String id, long timestamp, int method, boolean isMaster, int ack, int from, byte[] body) {
            this.id = id;
            this.timestamp = timestamp;
            this.method = method;
            this.isMaster = isMaster;
            this.ack = ack;
            this.from = from;
            this.body = body;
        }
    }
}
