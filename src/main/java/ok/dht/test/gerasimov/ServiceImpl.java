package ok.dht.test.gerasimov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.gerasimov.lsm.BaseEntry;
import ok.dht.test.gerasimov.lsm.Config;
import ok.dht.test.gerasimov.lsm.Dao;
import ok.dht.test.gerasimov.lsm.Entry;
import ok.dht.test.gerasimov.lsm.artyomdrozdov.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private static final String INVALID_ID_MESSAGE = "Invalid id";
    private static final int FLUSH_THRESHOLD_BYTES = 4096;

    private HttpServer httpServer;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ValidationService validationService;
    private final ServiceConfig serviceConfig;

    public ServiceImpl(ServiceConfig serviceConfig, ValidationService validationService) throws IOException {
        this.serviceConfig = serviceConfig;
        this.validationService = validationService;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        this.httpServer = new HttpServer(createServerConfig(serviceConfig)) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    for (Session session : thread.selector) {
                        session.socket().close();
                    }
                }

                super.stop();
            }
        };
        this.dao = new MemorySegmentDao(
                new Config(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES)
        );
        httpServer.start();
        httpServer.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGetRequest(@Param(value = "id", required = true) String id) throws IOException {
        if (!validationService.checkId(id)) {
            return createBadRequest(INVALID_ID_MESSAGE);
        }

        try {
            MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
            Entry<MemorySegment> entry = dao.get(memorySegmentId);

            if (entry == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }

            byte[] data = entry.value().toByteArray();
            return new Response(Response.OK, data);
        } catch (IOException e) {
            return createServerError("IOException");
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePutRequest(@Param(value = "id", required = true) String id, Request request) {
        if (!validationService.checkId(id)) {
            return createBadRequest(INVALID_ID_MESSAGE);
        }

        MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
        MemorySegment memorySegmentValue = MemorySegment.ofArray(request.getBody());
        Entry<MemorySegment> entry = new BaseEntry<>(memorySegmentId, memorySegmentValue);
        dao.upsert(entry);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDeleteRequest(@Param(value = "id", required = true) String id) {
        if (!validationService.checkId(id)) {
            return createBadRequest(INVALID_ID_MESSAGE);
        }

        MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
        Entry<MemorySegment> entry = new BaseEntry<MemorySegment>(memorySegmentId, null);
        dao.upsert(entry);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static HttpServerConfig createServerConfig(ServiceConfig serviceConfig) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();

        acceptor.port = serviceConfig.selfPort();
        acceptor.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};

        return httpServerConfig;
    }

    private static Response createBadRequest(String message) {
        return new Response(Response.BAD_REQUEST, Utf8.toBytes(message));
    }

    private static Response createServerError(String message) {
        return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(message));
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        private ValidationService createValidationService() {
            return new ValidationService();
        }

        @Override
        public Service create(ServiceConfig config) {
            try {
                return new ServiceImpl(config, createValidationService());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
