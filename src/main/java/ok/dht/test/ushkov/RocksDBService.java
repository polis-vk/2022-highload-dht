package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ushkov.http.AsyncHttpServer;
import ok.dht.test.ushkov.http.AsyncHttpServerConfig;
import one.nio.http.HttpClient;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RocksDBService implements Service {
    public static final String V0_ENTITY = "/v0/entity";
    public static final int N_SELECTORS = 5;
    public static final int N_WORKERS = 5;
    public static final int QUEUE_CAP = 100;
    public static final long STOP_TIMEOUT_MINUTES = 1;
    public static final int REDIRECT_TIMEOUT_MILLIS = 600;

    private final ServiceConfig config;
    private RocksDB db;
    private AsyncHttpServer server;
    private final Map<String, HttpClient> clientPool = new HashMap<>();
    private final KeyManager keyManager = new ConsistentHashing();

    public RocksDBService(ServiceConfig config) {
        this.config = config;
        for (String url : this.config.clusterUrls()) {
            keyManager.addNode(url);
        }
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

        for (String url : config.clusterUrls()) {
            ConnectionString conn = new ConnectionString(url);
            HttpClient client = new HttpClient(conn);
            clientPool.put(url, client);
        }

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

    private AsyncHttpServer createHttpServer(AsyncHttpServerConfig httpConfig) throws IOException {
        return new AsyncHttpServer(httpConfig) {
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
                                    String id = request.getParameter("id=");
                                    requireNotEmpty(id);
                                    requireKeyOwnership(id);
                                    Response response = getEntity(id);
                                    session.sendResponse(response);
                                }
                                default -> throw new BadPathException();
                            }
                        }
                        case Request.METHOD_PUT -> {
                            switch (request.getPath()) {
                                case V0_ENTITY -> {
                                    String id = request.getParameter("id=");
                                    requireNotEmpty(id);
                                    requireKeyOwnership(id);
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
                                    String id = request.getParameter("id=");
                                    requireNotEmpty(id);
                                    requireKeyOwnership(id);
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
                } catch (BadPathException | EmptyIdException e) {
                    sendEmptyResponse(session, Response.BAD_REQUEST);
                } catch (KeyOwnershipException e) {
                    String url = keyManager.getNodeIdByKey(e.key);
                    Response response = redirectRequest(url, request);
                    String statusCode = response.getHeaders()[0];
                    session.sendResponse(new Response(statusCode, response.getBody()));
                }
            }
        };
    }

    private static void requireNotEmpty(String id) throws EmptyIdException {
        if (id == null || id.isEmpty()) {
            throw new EmptyIdException();
        }
    }

    private void requireKeyOwnership(String id) throws KeyOwnershipException {
        String nodeId = keyManager.getNodeIdByKey(id);
        if (!nodeId.equals(config.selfUrl())) {
            throw new KeyOwnershipException(id);
        }
    }

    private static void sendEmptyResponse(HttpSession session, String code) throws IOException {
        Response response = new Response(code, Response.EMPTY);
        session.sendResponse(response);
    }

    private Response redirectRequest(String url, Request request) {
        Request redirectedRequest = new Request(request);
        try {
            return clientPool.get(url).invoke(redirectedRequest, REDIRECT_TIMEOUT_MILLIS);
        } catch (Exception e1) {
            return new Response(Response.BAD_GATEWAY, Response.EMPTY);
        }
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
        if (server == null && db == null) {
            return CompletableFuture.completedFuture(null);
        }

        clientPool.forEach((url, client) -> client.close());
        clientPool.clear();

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

    private static class FlowControlException extends Exception {
    }

    private static class MethodNotAllowedException extends FlowControlException {
    }

    private static class BadPathException extends FlowControlException {
    }

    private static class EmptyIdException extends FlowControlException {
    }

    private static class KeyOwnershipException extends FlowControlException {
        private final String key;

        public KeyOwnershipException(String key) {
            this.key = key;
        }
    }

    @ServiceFactory(stage = 3, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
