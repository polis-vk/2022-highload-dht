package ok.dht.test.shashulovskiy;

import com.google.common.base.Throwables;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shashulovskiy.sharding.ConsistentHashingShardingManager;
import ok.dht.test.shashulovskiy.sharding.Shard;
import ok.dht.test.shashulovskiy.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    private static final int SHARD_VNODES = 5;

    private final ServiceConfig config;
    // TODO Use HttpServer
    private HttpServerImpl server;
    private final HttpClient client = HttpClient.newHttpClient();

    private DB dao;

    private final ShardingManager shardingManager;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
        this.shardingManager = new ConsistentHashingShardingManager(
                config.clusterUrls(),
                config.selfUrl(),
                SHARD_VNODES
        );
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);

        this.dao = factory.open(config.workingDir().toFile(), options);

        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
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
        try {
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

            Shard shard = shardingManager.getShard(id);

            if (shard == null) {
                try {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> {
                            System.out.printf("Getting %s from %s\n", id, config.selfUrl());
                            byte[] result = dao.get(Utf8.toBytes(id));
                            if (result == null) {
                                return new Response(Response.NOT_FOUND, Response.EMPTY);
                            } else {
                                return new Response(Response.OK, result);
                            }
                        }
                        case Request.METHOD_PUT -> {
                            System.out.printf("Saving %s:%s to %s\n", id, Utf8.toString(request.getBody()), config.selfUrl());
                            dao.put(Utf8.toBytes(id), request.getBody());

                            return new Response(Response.CREATED, Response.EMPTY);
                        }
                        case Request.METHOD_DELETE -> {
                            dao.delete(Utf8.toBytes(id));

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
                System.out.printf("Redirecting %s to %s\n", config.selfUrl(), shard.getShardUrl());
                try {
                    HttpResponse<byte[]> response = client.send(
                            HttpRequest
                                    .newBuilder()
                                    .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
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
        } catch (Throwable e) {
            System.err.println("Error!" + Throwables.getStackTraceAsString(e));
            System.err.println("Error!" + e.getCause().getMessage());
            return null;
        }
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
