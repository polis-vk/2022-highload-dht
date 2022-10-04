package ok.dht.test.gerasimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.lsm.Config;
import ok.dht.test.gerasimov.lsm.artyomdrozdov.MemorySegmentDao;
import one.nio.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);
    private static final String INVALID_ID_MESSAGE = "Invalid id";
    private static final int FLUSH_THRESHOLD_BYTES = 4194304;

    private final ServiceConfig serviceConfig;
    private final HttpServer httpServer;

    private DaoService daoService;

    public ServiceImpl(ServiceConfig serviceConfig) {
        try {
            this.serviceConfig = serviceConfig;
            this.httpServer = new Server(serviceConfig.selfPort());
            httpServer.addRequestHandlers(this);
        } catch (IOException e) {
            throw new ServerException("Error during server create", e);
        }
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            this.daoService = new DaoService(
                    new MemorySegmentDao(
                            new Config(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES)
                    )
            );
            LOG.info("DAO created");
            httpServer.start();
        } catch (IOException e) {
            LOG.error("Error during server startup");
            throw new ServerException("DAO can not be created", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        try {
            httpServer.stop();
            daoService.close();
        } catch (IOException e) {
            LOG.error("Error during server shutdown");
            throw new ServerException("Error during DAO close", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGetRequest(@Param(value = "id", required = true) String id) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        try {
            Optional<byte[]> dataOpt = daoService.get(id);
            if (dataOpt.isEmpty()) {
                return ResponseEntity.notFound();
            }

            return ResponseEntity.ok(dataOpt.get());
        } catch (IOException e) {
            return ResponseEntity.internalError("IOException");
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePutRequest(@Param(value = "id", required = true) String id, Request request) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        daoService.upsert(id, request.getBody());
        return ResponseEntity.created();
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDeleteRequest(@Param(value = "id", required = true) String id) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        daoService.delete(id);
        return ResponseEntity.accepted();
    }

    private static boolean checkId(String id) {
        return !id.isBlank() && id.chars().noneMatch(Character::isWhitespace);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
