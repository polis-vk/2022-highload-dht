package ok.dht.test.galeev;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class CustomHttpServer extends HttpServer {
    private final PathMapper defaultMapper = new PathMapper();
    private final ExecutorService executorService;
    private static final String TOO_MANY_REQUESTS = "429 Too Many Requests";
    private static final Set<Integer> SUPPORTED_METHODS = new HashSet<>();
    private boolean isStopping;

    public CustomHttpServer(HttpServerConfig config,
                            ExecutorService executorService) throws IOException {
        super(config);
        this.executorService = executorService;
    }

    @Override
    public void handleDefault(Request request,
                              HttpSession session) throws IOException {
        if (SUPPORTED_METHODS.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        } else {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new UniversalHttpSession(socket, this);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            if (thread.isAlive()) {
                for (Session session : thread.selector) {
                    if (session.socket().isOpen()) {
                        session.socket().close();
                    }
                }
                thread.interrupt();
            }
        }
        super.stop();
    }

    public void addRequestHandlers(String path, int[] methods, RequestHandler handler) {
        for (int i : methods) {
            SUPPORTED_METHODS.add(i);
        }

        defaultMapper.add(path, methods, handler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (isStopping) {
            session.sendError(Response.SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }
        String path = request.getPath();
        int method = request.getMethod();

        RequestHandler requestHandler = defaultMapper.find(path, method);

        if (requestHandler == null) {
            // If handler wasn't found - trying to understand whether its METHOD_NOT_ALLOWED or BAD_REQUEST
            handleDefault(request, session);
        } else {
            // If handler was found - then everything is OK
            executorService.submit(new RunnableForRequestHandler(requestHandler, request, session));
        }
    }

    public void prepareStopping() {
        isStopping = true;
    }

    @SuppressWarnings("ClassCanBeRecord")
    public static class RunnableForRequestHandler implements Runnable {
        private final HttpSession session;
        private final Request request;
        private final RequestHandler requestHandler;

        public RunnableForRequestHandler(RequestHandler requestHandler, Request request, HttpSession session) {
            this.request = request;
            this.session = session;
            this.requestHandler = requestHandler;
        }

        @Override
        public void run() {
            try {
                requestHandler.handleRequest(request, session);
            } catch (IOException e) {
                throw new UncheckedIOException("Too many answers. Can not write anything", e);
            }
        }

        public void rejectRequest() {
            try {
                session.sendError(TOO_MANY_REQUESTS, "Too many requests. Please try again later");
            } catch (IOException e) {
                throw new UncheckedIOException("Too many answers. Can not write anything", e);
            }
        }
    }
}
