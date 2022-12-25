package ok.dht.test.ponomarev.http;

import java.io.IOException;
import java.util.Set;
import ok.dht.test.ponomarev.http.conf.ServerConfiguration;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

public final class Server extends HttpServer {
    public Server(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        // TODO: Мб нам не нужна тут итерация по списку? Будет интересно посмотреть ускорит ли что-то в async-profiler
        // JIT?

        final Set<Integer> methods = ServerConfiguration.SUPPORTED_METHODS_BY_ENDPOINT.get(request.getPath());
        if (methods == null) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        if (methods != null && !methods.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }
}
