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
import one.nio.http.HttpSession;
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

    //region Handling Requests
    private void fail(String url) {
        breaker.fail(url);
        Constants.LOG.error("Failed to request to shard {}", url);
    }

    @Path(Constants.PATH)
    public void handle(@Param(value = "id") String id,
                       Request request,
                       @Param(value = "ack") Integer ack,
                       @Param(value = "from") Integer from,
                       HttpSession session) throws IOException {
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, getBytes("Id can not be null or empty!")));
            return;
        }

        int neighbours = from == null ? this.config.clusterUrls().size() : from;
        int quorum = ack == null ? neighbours / 2 + 1 : ack;

        if (quorum > neighbours || quorum <= 0) {
            session.sendResponse(new Response(Response.BAD_REQUEST,
                    getBytes("ack and from - two positive ints, ack <= from")));
            return;
        }

        List<String> urls = sharder.getShardUrls(id, neighbours);
        ResponseProcessor processor = new ResponseProcessor(request.getMethod(), quorum, neighbours);
        long timestamp = System.currentTimeMillis();

        try {
            byte[] body = request.getMethod() == Request.METHOD_PUT ? request.getBody() : null;
            request.setBody(UtilsClass.valueToSegment(new StoredValue(body, timestamp)));
        } catch (IOException e) {
            session.sendResponse(new Response(Response.BAD_REQUEST,
                    getBytes("Can't ask other replicas, %s$".formatted(e.getMessage()))));
            return;
        }

        for (final String url : urls) {
            collectResponse(id, request, session, processor, url);
        }
    }

    private void collectResponse(String id,
                                 Request request,
                                 HttpSession session,
                                 ResponseProcessor processor,
                                 String url) {
        CompletableFuture<Response> futureResponse = url.equals(config.selfUrl())
                ? CompletableFuture.supplyAsync(() -> handleV1(id, request))
                : handleProxy(url, request);
        futureResponse.whenCompleteAsync((response, throwable) -> {
            if (!processor.process(response)) {
                return;
            }
            try {
                session.sendResponse(processor.response());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    //endregion

    private CompletableFuture<Response> handleProxy(String url, Request request) {
        if (breaker.isWorking(url)) {
            HttpRequest proxyRequest = HttpRequest
                    .newBuilder(URI.create(url + request.getURI().replace(Constants.PATH, Constants.REPLICA_PATH)))
                    .method(
                            request.getMethodName(),
                            HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .build();
            return client.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .handleAsync((response, exception) -> {
                        if (exception != null || response.statusCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                            fail(url);
                            return null;
                        }
                        breaker.success(url);
                        return new Response(Integer.toString(response.statusCode()), response.body());
                    });
        }
        return CompletableFuture.completedFuture(null);
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
