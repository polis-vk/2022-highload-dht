package ok.dht.test.kazakov.service.http;

import ok.dht.Service;
import ok.dht.test.kazakov.service.DaoExecutorServiceHelper;
import ok.dht.test.kazakov.service.ExceptionUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class DaoHttpServer extends HttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(DaoHttpServer.class);

    // package-private in one-nio, so copy-pasted here
    public static final List<String> METHODS = List.of(
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
    );

    // as one-nio does not define it
    public static final String TOO_MANY_REQUESTS = "429 Too Many Requests";

    private final DaoHttpPathMapper pathMapper;

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
        this.pathMapper = new DaoHttpPathMapper();
        this.handlerExecutorService = handlerExecutorService;
        this.unsupportedMethodRequestHandler = new UnsupportedMethodRequestHandler();
    }

    @Override
    public HttpSession createSession(final Socket socket) throws RejectedSessionException {
        return new DaoHttpSession(socket, this);
    }

    public void addRequestHandlers(@Nonnull final String path,
                                   final int[] methods,
                                   @Nonnull final DaoHttpRequestHandler requestHandler) {
        pathMapper.add(path, methods, new AsynchronousRequestHandler(requestHandler));
        pathMapper.add(path, null, unsupportedMethodRequestHandler);
    }

    @Override
    public void handleRequest(@Nonnull final Request request, @Nonnull final HttpSession session) throws IOException {
        if (!(session instanceof DaoHttpSession)) {
            LOG.error("Session of unexpected class {} given", session.getClass());
            stopService(null);
            return;
        }

        final String path = request.getPath();
        final int method = request.getMethod();
        LOG.debug("{} {}", METHODS.get(method), request.getURI());

        final DaoHttpRequestHandler handler = pathMapper.find(path, method);
        if (handler != null) {
            handler.handleRequest(request, (DaoHttpSession) session);
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
        Exception stopException = ExceptionUtils.tryExecute(null, super::stop);

        // closing all connections
        for (final SelectorThread selector : selectors) {
            for (final Session session : selector.selector) {
                stopException = ExceptionUtils.tryExecute(stopException, session::close);
            }
        }

        if (stopException != null) {
            LOG.error("Exception caught during DaoHttpServer.stop()", stopException);
            throw new RuntimeException(stopException);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public void stopService(@Nullable final Throwable suppressedException) {
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
            try {
                DaoExecutorServiceHelper.shutdownGracefully(handlerExecutorService);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted during `DaoExecutorServiceHelper.shutdownGracefully(handlerExecutorService)`", e);
            }
        }
    }

    private static final class UnsupportedMethodRequestHandler implements DaoHttpRequestHandler {
        @Override
        public void handleRequest(final Request request,
                                  final DaoHttpSession session) throws IOException {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private final class AsynchronousRequestHandler implements DaoHttpRequestHandler {
        private final DaoHttpRequestHandler wrappedHandler;

        private AsynchronousRequestHandler(final DaoHttpRequestHandler wrappedHandler) {
            this.wrappedHandler = wrappedHandler;
        }

        @Override
        public void handleRequest(@Nonnull final Request request,
                                  @Nonnull final DaoHttpSession session) {
            final SynchronousRequestHandler requestHandler = new SynchronousRequestHandler(
                    wrappedHandler,
                    session,
                    request
            );
            try {
                handlerExecutorService.execute(requestHandler);
            } catch (final RejectedExecutionException rejectedExecutionException) {
                LOG.debug("Rejected execution of {} {} ({})",
                        METHODS.get(request.getMethod()),
                        request.getURI(),
                        rejectedExecutionException.getMessage());
                requestHandler.rejectRequest();
            }
        }
    }

    public final class SynchronousRequestHandler implements Runnable {

        private static final byte[] REJECTED_RESPONSE_BODY = Utf8.toBytes("Server is busy. Please, retry later.");
        private final DaoHttpRequestHandler wrappedHandler;
        private final DaoHttpSession session;
        private final Request request;

        private SynchronousRequestHandler(final DaoHttpRequestHandler wrappedHandler,
                                          final DaoHttpSession session,
                                          final Request request) {
            this.wrappedHandler = wrappedHandler;
            this.session = session;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                wrappedHandler.handleRequest(request, session);
            } catch (final RejectedExecutionException rejectedExecutionException) {
                LOG.debug("Rejected execution of {} {}",
                        METHODS.get(request.getMethod()),
                        request.getURI(),
                        rejectedExecutionException);
                rejectRequest();
            } catch (final Exception handleRequestException) {
                LOG.error("Fatal error on request handling", handleRequestException);
                stopService(handleRequestException);
            }
        }

        public void rejectRequest() {
            try {
                session.sendResponse(new Response(TOO_MANY_REQUESTS, REJECTED_RESPONSE_BODY));
            } catch (final IOException sendErrorException) {
                LOG.error("Could not send 'Too Many Requests' response", sendErrorException);
            }
        }
    }
}
