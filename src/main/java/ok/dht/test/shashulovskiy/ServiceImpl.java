package ok.dht.test.shashulovskiy;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shashulovskiy.hashing.MD5Hasher;
import ok.dht.test.shashulovskiy.sharding.ConsistentHashingShardingManager;
import ok.dht.test.shashulovskiy.sharding.Shard;
import ok.dht.test.shashulovskiy.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);
    private static final int VNODES_COUNT = 5;

    private final ServiceConfig config;
    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    private DB dao;

    private final ShardingManager shardingManager;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
        this.shardingManager = new ConsistentHashingShardingManager(
                config.clusterUrls(),
                config.selfUrl(),
                VNODES_COUNT,
                new MD5Hasher()
        );
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);

        this.dao = factory.open(config.workingDir().toFile(), options);

        server = new HttpServerImpl(createConfigFromPort(config.selfPort()), config.clusterUrls().size());
        server.addRequestHandlers(this);
        server.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response handle(Request request) {
        String id = request.getParameter("id=");
        if (id == null) {
            return new Response(
                    Response.BAD_REQUEST,
                    Utf8.toBytes("No id provided")
            );
        } else if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Utf8.toBytes("Empty id")
            );
        }

        byte[] idBytes = Utf8.toBytes(id);

        Shard shard = shardingManager.getShard(idBytes);

        if (shard == null) {
            try {
                switch (request.getMethod()) {
                    case Request.METHOD_GET -> {
                        byte[] result = dao.get(idBytes);
                        if (result == null) {
                            return new Response(Response.NOT_FOUND, Response.EMPTY);
                        } else {
                            return new Response(Response.OK, result);
                        }
                    }
                    case Request.METHOD_PUT -> {
                        dao.put(idBytes, request.getBody());

                        return new Response(Response.CREATED, Response.EMPTY);
                    }
                    case Request.METHOD_DELETE -> {
                        dao.delete(idBytes);

                        return new Response(Response.ACCEPTED, Response.EMPTY);
                    }
                    default -> {
                        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                    }
                }
            } catch (DBException exception) {
                LOG.error("Internal dao exception occurred on " + request.getPath(), exception);
                return new Response(
                        Response.INTERNAL_ERROR,
                        Utf8.toBytes("An error occurred when accessing database.")
                );
            }
        } else {
            try {
                HttpResponse<byte[]> response = client.send(
                        HttpRequest
                                .newBuilder()
                                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody() == null ? Response.EMPTY : request.getBody()))
                                .uri(URI.create(shard.getShardUrl() + request.getURI())).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                return new Response(Integer.toString(response.statusCode()), response.body());
            } catch (IOException e) {
                return new Response(
                        Response.SERVICE_UNAVAILABLE,
                        Utf8.toBytes("Internal shard error")
                );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Path("/stats/handledKeys")
    @RequestMethod(Request.METHOD_GET)
    public Response handleKeyStats() {
        return new Response(Response.OK, Utf8.toBytes(Long.toString(shardingManager.getHandledKeys())));
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
