package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ushkov.dao.BaseEntry;
import ok.dht.test.ushkov.dao.Entry;
import ok.dht.test.ushkov.dao.rocksdb.RocksDBDao;
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
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class RocksDBService implements Service {
    private final ServiceConfig config;
    private RocksDBDao dao;
    private HttpServer server;

    public RocksDBService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new RocksDBDao(config.workingDir().toString());
        server = createHttpServer(createHttpServerConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private HttpServer createHttpServer(HttpServerConfig config) throws IOException {
        return new HttpServer(config) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }

            @Override
            public synchronized void stop() {
                // HttpServer.stop() doesn't close sockets
                for (SelectorThread thread : selectors) {
                    for (Session session : thread.selector) {
                        session.socket().close();
                    }
                }

                super.stop();
            }
        };
    }

    private static HttpServerConfig createHttpServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        server = null;

        dao.close();
        dao = null;

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response entityGet(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            ByteBuffer key = ByteBuffer.wrap(Utf8.toBytes(id));
            Entry<ByteBuffer> entry = dao.get(key);
            if (entry == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            } else {
                return new Response(Response.OK, entry.value().array());
            }
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response entityPut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            ByteBuffer key = ByteBuffer.wrap(Utf8.toBytes(id));
            ByteBuffer value = ByteBuffer.wrap(request.getBody());
            Entry<ByteBuffer> entry = new BaseEntry<>(key, value);
            dao.upsert(entry);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response entityDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            ByteBuffer key = ByteBuffer.wrap(Utf8.toBytes(id));
            Entry<ByteBuffer> entry = new BaseEntry<>(key, null);
            dao.upsert(entry);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
