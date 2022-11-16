package ok.dht.test.frolovm;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerImpl.class);

    private static final int CORE_POLL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int KEEP_ALIVE_TIME = 0;
    private static final int QUEUE_CAPACITY = 128;

    private final ExecutorService requestService;

    public HttpServerImpl(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        requestService = new ThreadPoolExecutor(
                CORE_POLL_SIZE,
                CORE_POLL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY)
        );
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(Utils.emptyResponse(Response.BAD_REQUEST));
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        Runnable handleTask = () -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                sessionSendError(session, e);
            }
        };
        try {
            requestService.execute(handleTask);
        } catch (RejectedExecutionException exception) {
            LOGGER.error("If this task cannot be accepted for execution", exception);
        }
    }

    @Override
    public synchronized void stop() {
        closeSessions();
        super.stop();
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new HttpSession(socket, this) {

            @Override
            public synchronized void sendResponse(Response response) throws IOException {
                if (response instanceof RangeResponse) {
                    super.write(
                            new QueueItem() {
                                @Override
                                public int write(Socket socket) throws IOException {
                                    return socket.write(response.getBody(), 0, response.getBody().length);
                                }
                            }
                    );
                } else {
                    super.sendResponse(response);
                }
            }
        };
    }

    private void closeSessions() {
        for (SelectorThread selectorThread : selectors) {
            selectorThread.selector.forEach(Session::close);
        }
    }

    private void sessionSendError(HttpSession session, Exception e) {
        try {
            session.sendError(Response.BAD_REQUEST, e.getMessage());
            LOGGER.error("Can't handle request", e);
        } catch (IOException exception) {
            LOGGER.error("Can't send error message to Bad Request", exception);
        }
    }

    public void close() {
        this.stop();
        Utils.closeExecutorPool(requestService);
    }

}
