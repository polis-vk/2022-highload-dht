package ok.dht.test.armenakyan;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.armenakyan.distribution.SelfNodeHandler;
import ok.dht.test.armenakyan.distribution.coordinator.ClusterCoordinator;
import ok.dht.test.armenakyan.distribution.hashing.MD5KeyHasher;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class DhtService implements Service {
    private static final int SERVER_WORKERS = Runtime.getRuntime().availableProcessors();
    private static final String ID_PARAM = "id=";
    private static final String ACK_PARAM = "ack=";
    private static final String FROM_PARAM = "from=";

    private static final Set<Integer> ALLOWED_METHODS = Set.of(
            Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE
    );

    private final ServiceConfig serviceConfig;
    private HttpServer httpServer;
    private ClusterCoordinator coordinator;
    private ForkJoinPool internalPool;

    public DhtService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        internalPool = new ForkJoinPool(
                SERVER_WORKERS,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
        );

        httpServer = new DhtHttpServer(
                internalPool,
                ServiceUtils.createConfigFromPort(serviceConfig.selfPort()),
                this
        );

        coordinator = new ClusterCoordinator(
                serviceConfig,
                new MD5KeyHasher(),
                new SelfNodeHandler(serviceConfig.workingDir(), internalPool)
        );

        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        coordinator.close();
        internalPool.shutdownNow();

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

        String timestampHeader = request.getHeader(ServiceUtils.TIMESTAMP_HEADER);
        if (timestampHeader != null) {
            coordinator.handleLocally(id, request, session);
            return;
        }

        String ackParam = request.getParameter(ACK_PARAM);
        String fromParam = request.getParameter(FROM_PARAM);

        int from = serviceConfig.clusterUrls().size();
        if (fromParam != null) {
            try {
                from = Integer.parseInt(fromParam);
            } catch (NumberFormatException ignored) {
                // fallback to default
            }
        }

        int ack = from / 2 + 1;
        if (ackParam != null) {
            try {
                ack = Integer.parseInt(ackParam);
            } catch (NumberFormatException ignored) {
                // fallback to default
            }
        }

        if (ack > from || ack <= 0) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        coordinator.replicate(id, request, session, ack, from);
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig serviceConfig) {
            return new DhtService(serviceConfig);
        }
    }
}
