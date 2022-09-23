package ok.dht.test.kovalenko;

import ok.dht.ServiceConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class Server extends HttpServer {

    public Server(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new MyService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
