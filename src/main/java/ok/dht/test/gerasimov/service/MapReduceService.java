package ok.dht.test.gerasimov.service;

import ok.dht.test.gerasimov.client.CircuitBreakerClient;
import ok.dht.test.gerasimov.exception.MapReduceServiceException;
import ok.dht.test.gerasimov.mapreduce.MapReduceQuery;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.Shard;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import org.iq80.leveldb.DB;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Michael Gerasimov
 */
public class MapReduceService implements HandleService {
    private static final Set<Integer> ALLOWED_METHODS = Set.of(Request.METHOD_POST);
    private static final String ENDPOINT = "/v0/map-reduce";
    private static final String SLAVE_HEADER = "Slave";

    private final ConsistentHash<String> consistentHash;
    private final DB dao;
    private final int port;
    private final CircuitBreakerClient client;

    public MapReduceService(DB dao, ConsistentHash<String> consistentHash, int port, CircuitBreakerClient client) {
        this.consistentHash = consistentHash;
        this.dao = dao;
        this.port = port;
        this.client = client;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        MapReduceQuery mapReduceQuery = isValidRequest(request, session);

        if (mapReduceQuery == null) {
            return;
        }

        if (request.getHeader(SLAVE_HEADER) == null) {
            request.addHeader(SLAVE_HEADER + "to");

            List<Shard> shards = consistentHash.getShards();
            List<byte[]> results = new ArrayList<>();

            for (Shard shard : shards) {
                if (shard.getPort() != port) {
                    results.add(
                            client.circuitBreaker(
                                            createHttpRequest(shard, request, mapReduceQuery.getClass().getName()),
                                            shard
                                    )
                                    .join()
                                    .body()
                    );
                } else {
                    results.add(mapReduceQuery.map(dao));
                }
            }

            sendResponse(mapReduceQuery.reduce(results).getBytes(), session);
        } else {
            sendResponse(mapReduceQuery.map(dao), session);
        }
    }

    private void sendResponse(byte[] bytes, HttpSession session) {
        try {
            session.sendResponse(ResponseEntity.ok(bytes));
        } catch (IOException e) {
            throw new MapReduceServiceException("Can not send response", e);
        }
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;
    }

    private MapReduceQuery isValidRequest(Request request, HttpSession session) {
        try {
            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(ResponseEntity.methodNotAllowed());
                return null;
            }

            String className = request.getParameter("className=");

            MapReduceClassLoader classLoader = new MapReduceClassLoader();
            Class<?> mapReduceQueryClass = classLoader.defineClass(className, request.getBody());

            return (MapReduceQuery) mapReduceQueryClass.getDeclaredConstructor().newInstance();
        } catch (IOException e) {
            throw new MapReduceServiceException("Can not send response", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new MapReduceServiceException("Can not load map reduce query", e);
        }
    }

    private static HttpRequest createHttpRequest(Shard shard, Request request, String className) {
        URI uri = URI.create(
                String.format("%s:%d%s?className=%s", shard.getHost(), shard.getPort(), ENDPOINT, className)
        );

        return HttpRequest.newBuilder()
                .uri(uri)
                .header(SLAVE_HEADER, request.getHeader(SLAVE_HEADER))
                .POST(
                        HttpRequest.BodyPublishers.ofByteArray(request.getBody() == null ? new byte[0] : request.getBody())
                )
                .build();
    }
}

class MapReduceClassLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }
}
