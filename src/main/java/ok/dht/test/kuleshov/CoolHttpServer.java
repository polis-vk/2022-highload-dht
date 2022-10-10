package ok.dht.test.kuleshov;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.Set;

public class CoolHttpServer extends HttpServer {
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_DELETE,
            Request.METHOD_GET,
            Request.METHOD_PUT
    );

    public CoolHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            response = new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }

        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }

        super.stop();
    }
}
