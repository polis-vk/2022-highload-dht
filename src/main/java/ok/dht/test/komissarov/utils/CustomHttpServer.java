package ok.dht.test.komissarov.utils;

import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;

public class CustomHttpServer extends HttpServer {

    public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }
        super.stop();
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }
}
