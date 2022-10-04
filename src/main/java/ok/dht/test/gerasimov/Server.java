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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class Server extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final int DEFAULT_THREAD_POOL_SIZE = 16;

    private final ExecutorService executorService;

    public Server(int port) throws IOException {
        super(createHttpServerConfig(port));
        this.executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
        LOG.info("Server created");
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        LOG.info("Server is starting up");
        super.start();
        LOG.info("Server is started");
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
                    super.handleRequest(request, session);
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

        return httpServerConfig;
    }
}
