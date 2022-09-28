package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.anikina.dao.BaseEntry;
import ok.dht.test.anikina.dao.Config;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.dao.MemorySegmentDao;
import ok.dht.test.anikina.utils.MemorySegmentUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private static final Response BAD_REQUEST_RESPONSE =
            new Response(Response.BAD_REQUEST, Response.EMPTY);
    private final ServiceConfig config;
    private DatabaseHttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new DatabaseHttpServer(config);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    public static class DatabaseHttpServer extends HttpServer {
        private static final long FLUSH_THRESHOLD_BYTES = 1024 * 1024;
        private final MemorySegmentDao dao;

        public DatabaseHttpServer(ServiceConfig config) throws IOException {
            super(createHttpServerConfig(config.selfPort()));
            this.dao = new MemorySegmentDao(
                    new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        }

        private static HttpServerConfig createHttpServerConfig(int port) {
            HttpServerConfig httpConfig = new HttpServerConfig();
            AcceptorConfig acceptorConfig = new AcceptorConfig();
            acceptorConfig.port = port;
            acceptorConfig.reusePort = true;
            httpConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
            return httpConfig;
        }

        private static boolean isInvalid(String key) {
            return key == null || key.isEmpty();
        }

        @Path("/v0/entity")
        @RequestMethod(Request.METHOD_GET)
        public Response get(@Param(value = "id", required = true) String key) {
            if (isInvalid(key)) {
                return BAD_REQUEST_RESPONSE;
            }
            byte[] value = getFromDao(key);
            if (value == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return new Response(Response.OK, value);
        }

        @Path("/v0/entity")
        @RequestMethod(Request.METHOD_PUT)
        public Response upsert(@Param("id") String key, Request request) {
            if (isInvalid(key)) {
                return BAD_REQUEST_RESPONSE;
            }
            insertIntoDao(key, request.getBody());
            return new Response(Response.CREATED, Response.EMPTY);
        }

        @Path("/v0/entity")
        @RequestMethod(Request.METHOD_DELETE)
        public Response delete(@Param("id") String key) {
            if (isInvalid(key)) {
                return BAD_REQUEST_RESPONSE;
            }
            insertIntoDao(key, null);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }

        @Override
        public void handleDefault(Request request, HttpSession session) throws IOException {
            session.sendResponse(BAD_REQUEST_RESPONSE);
        }

        public void close() throws IOException {
            dao.close();
            stop();
        }

        private byte[] getFromDao(String key) {
            Entry<MemorySegment> entry = dao.get(MemorySegmentUtils.fromString(key));
            return entry == null ? null : MemorySegmentUtils.toBytes(entry.value());
        }

        private void insertIntoDao(String key, byte[] bytes) {
            dao.upsert(new BaseEntry<>(
                    MemorySegmentUtils.fromString(key),
                    MemorySegmentUtils.fromBytes(bytes))
            );
        }
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
