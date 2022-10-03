package ok.dht.test.galeev;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.http.VirtualHost;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

public class CustomHttpServer extends HttpServer {
    private final PathMapper defaultMapper = new PathMapper();
    private final ExecutorService executorService;
    private static final String TOO_MANY_REQUESTS = "429 Too Many Requests";

    public CustomHttpServer(HttpServerConfig config, ExecutorService executorService, Object... routers) throws IOException {
        super(config, routers);
        this.executorService = executorService;
    }

    @Override
    public void handleDefault(Request request,
                              HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
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
        RequestHandler handler = findHandlerByHost(request);
        if (handler == null) {
            handler = defaultMapper.find(request.getPath(), request.getMethod());
        }

        if (handler != null) {
            handler.handleRequest(request, session);
        } else {
            handleDefault(request, session);
        }
    }

    @Override
    public void addRequestHandlers(Object router) {
        ArrayList<Class> supers = new ArrayList<>(4);
        for (Class<?> cls = router.getClass(); cls != Object.class; cls = cls.getSuperclass()) {
            supers.add(cls);
        }

        for (int i = supers.size(); --i >= 0; ) {
            Class<?> cls = supers.get(i);

            for (Method m : cls.getMethods()) {
                Path annotation = m.getAnnotation(Path.class);
                if (annotation == null) {
                    continue;
                }

                RequestMethod requestMethod = m.getAnnotation(RequestMethod.class);
                int[] methods = requestMethod == null ? null : requestMethod.value();

                for (String path : annotation.value()) {
                    if (!path.startsWith("/")) {
                        throw new IllegalArgumentException("Path '" + path + "' is not absolute");
                    }
                    defaultMapper.add(path, methods, (request, session)
                            -> executorService.submit(new RunnableForRequestHandler(request, session, m, router)));
                }
            }
        }
    }

    public static class RunnableForRequestHandler implements Runnable {
        private final Request request;
        private final HttpSession session;
        private final Method m;
        private final Object router;

        public RunnableForRequestHandler(Request request, HttpSession session, Method m, Object router) {
            this.request = request;
            this.session = session;
            this.m = m;
            this.router = router;
        }

        @Override
        public void run() {
            try {
                switch (request.getMethod()) {
                    case Request.METHOD_GET, Request.METHOD_DELETE -> session.sendResponse(
                            (Response) m.invoke(router, request.getParameter("id="))
                    );
                    case Request.METHOD_PUT -> session.sendResponse(
                            (Response) m.invoke(router, request, request.getParameter("id="))
                    );
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Can not access method with name: " + m.getName(), e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw new UncheckedIOException("Thread with name: " + Thread.currentThread().getName()
                            + " produced IOException with name: " + m.getName(), (IOException) cause);
                } else {
                    throw new RuntimeException("Method with name: " + m.getName() + " produced exception", e);
                }
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
