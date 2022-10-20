package ok.dht.test.kazakov.service.http;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

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

    public DaoHttpServer(@Nonnull final HttpServerConfig config, @Nonnull final Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleRequest(@Nonnull final Request request, @Nonnull final HttpSession session) throws IOException {
        LOG.trace("{} {}", METHODS[request.getMethod()], request.getPath());
        super.handleRequest(request, session);
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
}
