package ok.dht.test.armenakyan;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.armenakyan.sharding.ClusterCoordinatorShardHandler;
import ok.dht.test.armenakyan.sharding.SelfShardHandler;
import ok.dht.test.armenakyan.sharding.ShardRequestHandler;
import ok.dht.test.armenakyan.sharding.hashing.MD5KeyHasher;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DhtService implements Service {
    private static final String ID_PARAM = "id=";

    private static final Set<Integer> ALLOWED_METHODS = Set.of(
            Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE
    );

    private final ServiceConfig serviceConfig;
    private HttpServer httpServer;
    private ShardRequestHandler requestHandler;

    public DhtService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        httpServer = new DhtHttpServer(Runtime.getRuntime().availableProcessors(),
                ServiceUtils.createConfigFromPort(serviceConfig.selfPort()),
                this);

        SelfShardHandler daoHandler = new SelfShardHandler(serviceConfig.workingDir());

        MD5KeyHasher keyHasher;
        try {
            keyHasher = new MD5KeyHasher();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        requestHandler = new ClusterCoordinatorShardHandler(
                serviceConfig.selfUrl(),
                daoHandler,
                serviceConfig.clusterUrls(),
                keyHasher
        );

        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        requestHandler.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!ALLOWED_METHODS.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }

        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isBlank()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        session.sendResponse(requestHandler.handleForKey(id, request));
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig serviceConfig) {
            return new DhtService(serviceConfig);
        }
    }
}
