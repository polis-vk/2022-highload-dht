package ok.dht.test.nadutkin.impl;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.nadutkin.impl.replicas.ReplicaService;
import ok.dht.test.nadutkin.impl.replicas.ResponseProcessor;
import ok.dht.test.nadutkin.impl.shards.CircuitBreaker;
import ok.dht.test.nadutkin.impl.shards.JumpHashSharder;
import ok.dht.test.nadutkin.impl.shards.Sharder;
import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.utils.StoredValue;
import ok.dht.test.nadutkin.impl.utils.UtilsClass;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.nadutkin.impl.utils.UtilsClass.getBytes;

public class ServiceImpl extends ReplicaService {
    private Sharder sharder;
    private HttpClient client;
    private CircuitBreaker breaker;

    public ServiceImpl(ServiceConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        this.sharder = new JumpHashSharder(config.clusterUrls());
        this.client = HttpClient.newHttpClient();
        this.breaker = new CircuitBreaker(config.clusterUrls());
        return super.start();
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        return super.stop();
    }

    //region Handling Requests
    private void fail(String url) {
        breaker.fail(url);
        Constants.LOG.error("Failed to request to shard {}", url);
    }

    @Path(Constants.PATH)
    public Response handle(@Param(value = "id") String id,
                           Request request,
                           @Param(value = "ack") Integer ack,
                           @Param(value = "from") Integer from) throws IOException {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, getBytes("Id can not be null or empty!"));
        }
        int neighbours = from == null ? this.config.clusterUrls().size() : from;
        int quorum = ack == null ? neighbours / 2 + 1 : ack;

        if (quorum > neighbours || quorum <= 0) {
            return new Response(Response.BAD_REQUEST,
                    getBytes("ack and from - two positive ints, ack <= from"));
        }
        List<String> urls = sharder.getShardUrls(id, neighbours);
        ResponseProcessor processor = new ResponseProcessor(request.getMethod(), quorum);
        long timestamp = System.currentTimeMillis();
        try {
            byte[] body = request.getMethod() == Request.METHOD_PUT ? request.getBody() : null;
            request.setBody(UtilsClass.valueToSegment(new StoredValue(body, timestamp)));
        } catch (IOException e) {
            return new Response(Response.BAD_REQUEST,
                    getBytes("Can't ask other replicas, %s$".formatted(e.getMessage())));
        }
        for (String url : urls) {
            if (breaker.isWorking(url)) {
                boolean done = processor
                        .process(url.equals(config.selfUrl())
                                ? handleV1(id, request)
                                : handleProxy(url, request));
                if (done) {
                    break;
                }
            }
        }
        return processor.response();
    }

    //endregion

    private Response handleProxy(String url, Request request) {
        try {
            HttpRequest proxyRequest = HttpRequest
                    .newBuilder(URI.create(url + request.getURI().replace(Constants.PATH, Constants.REPLICA_PATH)))
                    .method(
                            request.getMethodName(),
                            HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .build();
            HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                fail(url);
            }
            breaker.success(url);
            return new Response(Integer.toString(response.statusCode()), response.body());
        } catch (InterruptedException | IOException exception) {
            Constants.LOG.error("Server caught an exception at url {}", url);
            fail(url);
        }
        return null;
    }

    @Path("/statistics/stored")
    @RequestMethod(Request.METHOD_GET)
    public Response getNumber() {
        return new Response(Response.OK, getBytes(storedData.toString()));
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
