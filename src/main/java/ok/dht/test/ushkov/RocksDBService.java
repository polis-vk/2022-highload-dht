package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ushkov.http.AsyncHttpServer;
import ok.dht.test.ushkov.http.AsyncHttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RocksDBService implements Service {
    public static final String V0_ENTITY = "/v0/entity";
    public static final int N_SELECTORS = 5;
    public static final int N_WORKERS = 5;
    public static final int QUEUE_CAP = 100;
    public static final long STOP_TIMEOUT_MINUTES = 1;

    private final ServiceConfig config;
    public RocksDB db;
    private AsyncHttpServer server;

    public RocksDBService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        
        AsyncHttpServerConfig httpServerConfig =
                createHttpServerConfigFromPort(config.selfPort());
        server = createHttpServer(httpServerConfig);
        server.start();
        
        return CompletableFuture.completedFuture(null);
    }

    private static AsyncHttpServerConfig createHttpServerConfigFromPort(int port) {
        AsyncHttpServerConfig httpConfig = new AsyncHttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.selectors = N_SELECTORS;
        httpConfig.workers = N_WORKERS;
        httpConfig.queueCapacity = QUEUE_CAP;
        return httpConfig;
    }
    
    private AsyncHttpServer createHttpServer(AsyncHttpServerConfig config) throws IOException {
        return new AsyncHttpServer(config) {
            @Override
            protected void handleRequestAsync(Request request, HttpSession session) {
                try {
                    processRequest(request, session);
                } catch (Exception e) {
                    try {
                        session.sendError(Response.INTERNAL_ERROR, "");
                    } catch (IOException e1) {
                        LOG.error("Could not send '500 Internal Server Error' to client", e1);
                    }
                }
            }

            private void processRequest(Request request, HttpSession session) throws IOException, RocksDBException {
                try {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> {
                            switch (request.getPath()) {
                                case V0_ENTITY -> {
                                    String id = requireNotEmpty(request.getParameter("id="));
                                    Response response = getEntity(id);
                                    session.sendResponse(response);
                                }
                                default -> throw new BadPathException();
                            }
                        }
                        case Request.METHOD_PUT -> {
                            switch (request.getPath()) {
                                case V0_ENTITY -> {
                                    String id = requireNotEmpty(request.getParameter("id="));
                                    byte[] body = request.getBody();
                                    Response response = putEntity(id, body);
                                    session.sendResponse(response);
                                }
                                default -> throw new BadPathException();
                            }
                        }
                        case Request.METHOD_DELETE -> {
                            switch (request.getPath()) {
                                case V0_ENTITY -> {
                                    String id = requireNotEmpty(request.getParameter("id="));
                                    Response response = deleteEntity(id);
                                    session.sendResponse(response);
                                }
                                default -> throw new BadPathException();
                            }
                        }
                        default -> throw new MethodNotAllowedException();
                    }
                } catch (MethodNotAllowedException e) {
                    sendEmptyResponse(session, Response.METHOD_NOT_ALLOWED);
                } catch (EmptyIdException | BadPathException e) {
                    sendEmptyResponse(session, Response.BAD_REQUEST);
                }
            }
        };
    }

    private static String requireNotEmpty(String id) throws EmptyIdException {
        if (id.isEmpty()) {
            throw new EmptyIdException();
        }
        return id;
    }

    private static void sendEmptyResponse(HttpSession session, String code) throws IOException {
        Response response = new Response(code, Response.EMPTY);
        session.sendResponse(response);
    }

    private Response getEntity(String id) throws RocksDBException {
        byte[] value = db.get(Utf8.toBytes(id));
        return value == null
                ? new Response(Response.NOT_FOUND, Response.EMPTY)
                : new Response(Response.OK, value);
    }

    public Response putEntity(String id, byte[] body) throws RocksDBException {
        db.put(Utf8.toBytes(id), body);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(String id) throws RocksDBException {
        db.delete(Utf8.toBytes(id));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            server.stop();
            try {
                server.awaitStop(STOP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server = null;
        });

        try {
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;

        return future;
    }

    private static class FlowControlException extends Exception {}

    private static class MethodNotAllowedException extends FlowControlException {}

    private static class BadPathException extends FlowControlException {}

    private static class EmptyIdException extends FlowControlException {}
}
