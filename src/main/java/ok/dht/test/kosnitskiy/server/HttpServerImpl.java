package ok.dht.test.kosnitskiy.server;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;

public class HttpServerImpl extends HttpServer {
    public HttpServerImpl(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response;
        if (request.getMethod() == Request.METHOD_POST) {
            response = new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        } else {
            response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        super.stop();
    }
}
