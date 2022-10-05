package ok.dht.test.gerasimov;

import ok.dht.test.gerasimov.exception.ServerException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Server extends HttpServer {
    private static final int DEFAULT_THREAD_POOL_SIZE = 32;
    private static final int SELECTOR_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int KEEP_A_LIVE_TIME_IN_NANOSECONDS = 0;
    private static final int WORK_QUEUE_CAPACITY = 256;
    private static final String ENDPOINT = "/v0/entity";

    private final ExecutorService executorService;
    private final ServiceImpl service;

    public Server(int port, ServiceImpl service) throws IOException {
        super(createHttpServerConfig(port));
        this.executorService = new ThreadPoolExecutor(
                DEFAULT_THREAD_POOL_SIZE,
                DEFAULT_THREAD_POOL_SIZE,
                KEEP_A_LIVE_TIME_IN_NANOSECONDS,
                TimeUnit.NANOSECONDS,
                new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY)
        );
        this.service = service;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        super.start();
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

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(() -> {
                try {
                    session.sendResponse(handleRequest(request));
                } catch (IOException e) {
                    throw new ServerException("Handler can not handle request", e);
                }
            });
        } catch (RejectedExecutionException e) {
            session.sendResponse(ResponseEntity.serviceUnavailable());
        }
    }

    private static HttpServerConfig createHttpServerConfig(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();

        acceptor.port = port;
        acceptor.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpServerConfig.selectors = SELECTOR_POOL_SIZE;

        return httpServerConfig;
    }

    private Response handleRequest(Request request) {
        try {
            String id = request.getParameter("id=");
            if (ENDPOINT.equalsIgnoreCase(request.getPath()) && id != null) {
                return switch (request.getMethod()) {
                    case Request.METHOD_GET -> service.handleGetRequest(id);
                    case Request.METHOD_PUT -> service.handlePutRequest(id, request);
                    case Request.METHOD_DELETE -> service.handleDeleteRequest(id);
                    default -> ResponseEntity.badRequest("Unsupported method");
                };
            }
            return ResponseEntity.badRequest("Unsupported path");
        } catch (Exception e) {
            return ResponseEntity.serviceUnavailable();
        }
    }
}
