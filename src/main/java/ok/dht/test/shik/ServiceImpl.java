package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shik.sharding.ConsistentHash;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.workers.WorkersConfig;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.server.ServerConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class ServiceImpl implements Service {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);
    private static final String URL_INFIX = "/v0/entity?id=";

    private final ServiceConfig config;
    private final WorkersConfig workersConfig;
    private final ConsistentHash consistentHash;

    private CustomHttpServer server;
    private HttpClient client;
    private DB levelDB;

    public ServiceImpl(ServiceConfig config) {
        this(config, new WorkersConfig(), new ShardingConfig());
    }

    public ServiceImpl(ServiceConfig config, WorkersConfig workersConfig, ShardingConfig shardingConfig) {
        this.config = config;
        this.workersConfig = workersConfig;
        consistentHash = new ConsistentHash(shardingConfig.getVNodesNumber(), config.clusterUrls());
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            levelDB = Iq80DBFactory.factory.open(config.workingDir().toFile(), new Options());
        } catch (IOException e) {
            LOG.error("Error while starting database: ", e);
            throw e;
        }
        client = HttpClient.newHttpClient();
        server = new CustomHttpServer(createHttpConfig(config), workersConfig);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        server.stop();
        try {
            levelDB.close();
        } catch (IOException e) {
            LOG.error("Error while closing: ", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (notValidId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        byte[] key = id.getBytes(StandardCharsets.UTF_8);
        String shardUrl = consistentHash.getShardUrlByKey(key);

        if (config.selfUrl().equals(shardUrl)) {
            byte[] value = levelDB.get(key);
            return value == null ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(value);
        } else {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(shardUrl + URL_INFIX + id)).GET().build();
            return proxyRequest(httpRequest, httpResponse -> {
                Response proxyResponse = checkProxyResponse(httpRequest, httpResponse);
                if (proxyResponse != null) {
                    return proxyResponse;
                }
                byte[] value = null;
                if (httpResponse != null) {
                    value = httpResponse.body();
                }
                return value == null ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(value);
            });
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (notValidId(id) || !isValidBody(request)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] value = request.getBody();
        byte[] key = id.getBytes(StandardCharsets.UTF_8);
        String shardUrl = consistentHash.getShardUrlByKey(key);
        if (config.selfUrl().equals(shardUrl)) {
            levelDB.put(key, value);
        } else {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(shardUrl + URL_INFIX + id))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(value)).build();
            return proxyRequest(httpRequest, httpResponse ->
                Objects.requireNonNullElseGet(checkProxyResponse(httpRequest, httpResponse),
                    () -> new Response(Response.CREATED, Response.EMPTY)));
        }

        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(required = true, value = "id") String id) {
        if (notValidId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        byte[] key = id.getBytes(StandardCharsets.UTF_8);
        String shardUrl = consistentHash.getShardUrlByKey(key);
        if (config.selfUrl().equals(shardUrl)) {
            levelDB.delete(key);
        } else {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(shardUrl + URL_INFIX + id)).DELETE().build();
            return proxyRequest(httpRequest, httpResponse ->
                Objects.requireNonNullElseGet(checkProxyResponse(httpRequest, httpResponse),
                    () -> new Response(Response.ACCEPTED, Response.EMPTY)));
        }

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private Response checkProxyResponse(HttpRequest httpRequest, HttpResponse<byte[]> httpResponse) {
        if (httpResponse == null || httpResponse.statusCode() == 503) {
            LOG.error("Cannot proxy query, request " + httpRequest.uri().toString());
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
        if (httpResponse.statusCode() == 500) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        if (httpResponse.statusCode() == 404) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return null;
    }

    private Response proxyRequest(HttpRequest request, Function<HttpResponse<byte[]>, Response> processResponse) {
        CompletableFuture<HttpResponse<byte[]>> future =
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return future.thenApply(processResponse).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while processing async query ", e);
        } catch (ExecutionException e) {
            LOG.warn("Exception while processing async query ", e);
        }
        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    }

    private static boolean notValidId(String id) {
        return id == null || id.isEmpty();
    }

    private static boolean isValidBody(Request request) {
        return request.getBody() != null;
    }

    private static HttpServerConfig createHttpConfig(ServiceConfig config) {
        ServerConfig serverConfig = ServerConfig.from(new ConnectionString(config.selfUrl()));
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        httpServerConfig.acceptors = serverConfig.acceptors;
        Arrays.stream(httpServerConfig.acceptors).forEach(x -> x.reusePort = true);
        httpServerConfig.schedulingPolicy = serverConfig.schedulingPolicy;
        return httpServerConfig;
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
