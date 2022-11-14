package ok.dht.test.armenakyan;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;

public class DhtHttpServer extends HttpServer {
    public DhtHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    @Override
    public synchronized void stop() {
        for (var selectorThread: selectors) {
            for (var session: selectorThread.selector) {
                session.close(); // close selectors sessions
            }
        }
        super.stop();
    }
}
