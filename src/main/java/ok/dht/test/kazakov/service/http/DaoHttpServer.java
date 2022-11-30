package ok.dht.test.kazakov.service.http;

import ok.dht.Service;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class DaoHttpServer extends HttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(DaoHttpServer.class);

    // package-private in one-nio, so copy-pasted here
    private static final String[] METHODS = {
            "",
            "GET",
            "POST",
            "HEAD",
            "OPTIONS",
            "PUT",
            "DELETE",
            "TRACE",
            "CONNECT",
            "PATCH"
    };

    // as one-nio does not define it
    private static final String TOO_MANY_REQUESTS = "429 Too Many Requests";

    private final PathMapper pathMapper;

    private final ExecutorService handlerExecutorService;
    private final Service service;
    private final UnsupportedMethodRequestHandler unsupportedMethodRequestHandler;

    public static HttpServerConfig createConfigFromPort(final int port) {
        final HttpServerConfig httpConfig = new HttpServerConfig();
        final AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public DaoHttpServer(@Nonnull final HttpServerConfig config,
                         @Nonnull final ExecutorService handlerExecutorService,
                         @Nonnull final Service service) throws IOException {
        super(config);
        this.service = service;
        this.pathMapper = new PathMapper();
        this.handlerExecutorService = handlerExecutorService;
        this.unsupportedMethodRequestHandler = new UnsupportedMethodRequestHandler();
    }

    public void addRequestHandler(@Nonnull final String path,
                                  final int method,
                                  @Nonnull final RequestHandler requestHandler) {
        pathMapper.add(path, new int[]{method}, new AsynchronousRequestHandler(requestHandler));
        pathMapper.add(path, null, unsupportedMethodRequestHandler);
    }

    @Override
    public void handleRequest(@Nonnull final Request request, @Nonnull final HttpSession session) throws IOException {
        final String path = request.getPath();
        final int method = request.getMethod();
        LOG.trace("{} {}", METHODS[method], path);

        final RequestHandler handler = pathMapper.find(path, method);
        if (handler != null) {
            handler.handleRequest(request, session);
            return;
        }

        handleDefault(request, session);
    }

    @Override
    public void handleDefault(@Nonnull final Request request, @Nonnull final HttpSession session) throws IOException {
        final Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        super.stop();

        // closing all connections
        for (final SelectorThread selector : selectors) {
            for (final Session session : selector.selector) {
                session.close();
            }
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void stopService(@Nullable final Throwable suppressedException) {
        try {
            // do not wait for stop, because it might run on handlerExecutorService
            // and will be blocked by this thread
            service.stop();
        } catch (final IOException serviceStopException) {
            if (suppressedException != null) {
                serviceStopException.addSuppressed(suppressedException);
            }
            LOG.error("Fatal error on service stop", serviceStopException);

            // stop at least http server
            stop();
            handlerExecutorService.shutdownNow();
        }
    }

    private static final class UnsupportedMethodRequestHandler implements RequestHandler {
        @Override
        public void handleRequest(final Request request,
                                  final HttpSession session) throws IOException {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private final class AsynchronousRequestHandler implements RequestHandler {
        private final RequestHandler wrappedHandler;

        private AsynchronousRequestHandler(final RequestHandler wrappedHandler) {
            this.wrappedHandler = wrappedHandler;
        }

        @Override
        public void handleRequest(@Nonnull final Request request,
                                  @Nonnull final HttpSession session) {
            final SynchronousRequestHandler requestHandler = new SynchronousRequestHandler(
                    wrappedHandler,
                    session,
                    request
            );
            try {
                handlerExecutorService.execute(requestHandler);
            } catch (final RejectedExecutionException rejectedExecutionException) {
                LOG.trace("Rejected execution of {} {} ({})",
                        METHODS[request.getMethod()],
                        request.getPath(),
                        rejectedExecutionException.getMessage());
                requestHandler.rejectRequest();
            }
        }
    }

    public final class SynchronousRequestHandler implements Runnable {

        private final RequestHandler wrappedHandler;
        private final HttpSession session;
        private final Request request;

        private SynchronousRequestHandler(final RequestHandler wrappedHandler,
                                          final HttpSession session,
                                          final Request request) {
            this.wrappedHandler = wrappedHandler;
            this.session = session;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                wrappedHandler.handleRequest(request, session);
            } catch (final Exception handleRequestException) {
                LOG.error("Fatal error on request handling", handleRequestException);
                stopService(handleRequestException);
            }
        }

        public void rejectRequest() {
            try {
                session.sendError(TOO_MANY_REQUESTS, "Server is busy. Please, retry later.");
            } catch (final IOException sendErrorException) {
                LOG.error("Fatal on 'Too Many Requests' response", sendErrorException);
                stopService(sendErrorException);
            }
        }
    }
}
