package ok.dht.test.shik;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.Set;

public class CustomHttpServer extends HttpServer {

    private static final String CLOSE_CONNECTION_HEADER = "Connection: close";
    private static final Log LOG = LogFactory.getLog(CustomHttpServer.class);
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
        Request.METHOD_GET,
        Request.METHOD_PUT,
        Request.METHOD_DELETE
    );

    public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = SUPPORTED_METHODS.contains(request.getMethod())
            ? new Response(Response.BAD_REQUEST, Response.EMPTY)
            : new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                Response response = new Response(Response.OK, Response.EMPTY);
                response.addHeader(CLOSE_CONNECTION_HEADER);
                byte[] responseBytes = response.toBytes(false);
                try {
                    session.write(responseBytes, 0, responseBytes.length);
                } catch (IOException e) {
                    LOG.error("Error while sending client info about closing socket", e);
                }
                session.socket().close();
            }
        }
        super.stop();
    }

}
