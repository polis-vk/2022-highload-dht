package ok.dht.test.nadutkin.impl;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.nadutkin.database.BaseEntry;
import ok.dht.test.nadutkin.database.Config;
import ok.dht.test.nadutkin.database.Entry;
import ok.dht.test.nadutkin.database.impl.MemorySegmentDao;
import ok.dht.test.nadutkin.impl.parallel.HighLoadHttpServer;
import ok.dht.test.nadutkin.impl.shards.CircuitBreaker;
import ok.dht.test.nadutkin.impl.shards.JumpHashSharder;
import ok.dht.test.nadutkin.impl.shards.Sharder;
import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.utils.UtilsClass;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static ok.dht.test.nadutkin.impl.utils.UtilsClass.getBytes;

public class ServiceImpl implements Service {
    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;
    private Sharder sharder;
    private HttpClient client;
    private CircuitBreaker breaker;
    private final AtomicInteger storedData = new AtomicInteger(0);

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        long flushThresholdBytes = 1 << 18;
        this.dao = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        this.server = new HighLoadHttpServer(UtilsClass.createConfigFromPort(config.selfPort()));
        int size = config.clusterUrls().size();
        this.sharder = new JumpHashSharder(size);
        this.client = HttpClient.newHttpClient();
        this.breaker = new CircuitBreaker(size);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        this.server.stop();
        this.dao.close();
        return CompletableFuture.completedFuture(null);
    }

    //region Handling Requests

    private MemorySegment getKey(String id) {
        return MemorySegment.ofArray(getBytes(id));
    }

    private Response upsert(String id, MemorySegment value, String goodResponse) {
        MemorySegment key = getKey(id);
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
        return new Response(goodResponse, Response.EMPTY);
    }

    private Response fail(int index) {
        breaker.fail(index);
        Constants.LOG.error("Failed to request to shard {}", config.clusterUrls().get(index));
        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    }

    @Path(Constants.PATH)
    public Response handle(@Param(value = "id", required = true) String id,
                           Request request) throws IOException {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, getBytes("Id can not be null or empty!"));
        }
        Integer index = sharder.getShard(id);
        if (breaker.isWorking(index)) {
            String url = config.clusterUrls().get(index);
            if (url.equals(config.selfUrl())) {
                switch (request.getMethod()) {
                    case Request.METHOD_GET -> {
                        Entry<MemorySegment> value = dao.get(getKey(id));
                        if (value == null) {
                            return new Response(Response.NOT_FOUND,
                                    getBytes("Can't find any value, for id %1$s".formatted(id)));
                        } else {
                            return new Response(Response.OK, value.value().toByteArray());
                        }
                    }
                    case Request.METHOD_PUT -> {
                        storedData.getAndIncrement();
                        return upsert(id, MemorySegment.ofArray(request.getBody()), Response.CREATED);
                    }
                    case Request.METHOD_DELETE -> {
                        return upsert(id, null, Response.ACCEPTED);
                    }
                    default -> {
                        return new Response(Response.METHOD_NOT_ALLOWED,
                                getBytes("Not implemented yet"));
                    }
                }
            } else {
                try {
                    HttpRequest proxyRequest = HttpRequest
                            .newBuilder(URI.create(url + request.getURI()))
                            .method(
                                    request.getMethodName(),
                                    HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                            .build();
                    HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                        return fail(index);
                    }
                    breaker.success(index);
                    return new Response(Integer.toString(response.statusCode()), response.body());
                } catch (InterruptedException exception) {
                    Constants.LOG.error("Server caught an exception at url {}", url);
                    return fail(index);
                }
            }
        } else {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    //endregion

    @Path("/statistics/stored")
    @RequestMethod(Request.METHOD_GET)
    public Response getNumber() {
        return new Response(Response.OK, getBytes(storedData.toString()));
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
