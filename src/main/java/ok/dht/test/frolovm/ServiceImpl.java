package ok.dht.test.frolovm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.dao.artyomdrozdov.BaseEntry;
import ok.dht.dao.artyomdrozdov.Config;
import ok.dht.dao.artyomdrozdov.Dao;
import ok.dht.dao.artyomdrozdov.Entry;
import ok.dht.dao.artyomdrozdov.MemorySegmentDao;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    public static final int FLUSH_THRESHOLD_BYTES = 1_048_576;
    private static final byte[] BAD_ID = Utf8.toBytes("Given id is bad.");

    private static final byte[] NO_SUCH_METHOD = Utf8.toBytes("No such method.");
    private final Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ServiceConfig config;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config, Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.config = config;
        this.dao = dao;
    }

    private static boolean checkId(String id) {
        return id != null && !id.isBlank();
    }

    private static MemorySegment stringToSegment(String value) {
        return MemorySegment.ofArray(Utf8.toBytes(value));
    }

    private static Response emptyResponse(String responseCode) {
        return new Response(responseCode, Response.EMPTY);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = createAcceptorConfig(port);
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    private static AcceptorConfig createAcceptorConfig(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        return acceptor;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            }
        };
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    private Response getHandler(String id) {
        Entry<MemorySegment> result = dao.get(stringToSegment(id));
        if (result == null) {
            return emptyResponse(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, result.value().toByteArray());
        }
    }

    @Path("/v0/entity")
    public Response entityHandler(@Param(value = "id", required = true) String id, Request request) {
        if (!checkId(id)) {
            return new Response(Response.BAD_REQUEST, BAD_ID);
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT:
                return putHandler(request, id);
            case Request.METHOD_GET:
                return getHandler(id);
            case Request.METHOD_DELETE:
                return deleteHandler(id);
            default:
                return new Response(Response.BAD_REQUEST, NO_SUCH_METHOD);
        }
    }

    private Response putHandler(Request request, String id) {
        MemorySegment bodySegment = MemorySegment.ofArray(request.getBody());
        dao.upsert(new BaseEntry<>(stringToSegment(id), bodySegment));
        return emptyResponse(Response.CREATED);
    }

    private Response deleteHandler(String id) {
        dao.upsert(new BaseEntry<>(stringToSegment(id), null));
        return emptyResponse(Response.ACCEPTED);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            Dao<MemorySegment, Entry<MemorySegment>> dao;
            try {
                dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Can't create database");
            }
            return new ServiceImpl(config, dao);
        }
    }
}
