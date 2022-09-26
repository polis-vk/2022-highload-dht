package ok.dht.test.pobedonostsev;

import java.io.IOException;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

public class CustomHttpServer extends HttpServer {
    public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
        session.close();
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }
}
