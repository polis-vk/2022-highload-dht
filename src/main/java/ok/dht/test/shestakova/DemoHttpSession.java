package ok.dht.test.shestakova;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;

public class DemoHttpSession extends HttpSession {
    public DemoHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    public synchronized void sendResponse(Response response) throws IOException {
        if (!(response instanceof ChunkedResponse)) {
            super.sendResponse(response);
            return;
        }

        Request handling = this.handling;
        if (handling == null) {
            throw new IOException("Out of order response");
        }

        server.incRequestsProcessed();

        String connection = handling.getHeader("Connection:");
        boolean keepAlive = handling.isHttp11()
                ? !"close".equalsIgnoreCase(connection)
                : "Keep-Alive".equalsIgnoreCase(connection);
        response.addHeader(keepAlive ? "Connection: Keep-Alive" : "Connection: close");
        response.addHeader("Transfer-Encoding: chunked");

        super.writeResponse(response, false);
        super.write(new MyQueueItem((ChunkedResponse) response));

        if (!keepAlive) scheduleClose();

        this.handling = handling = pipeline.pollFirst();
        if (this.handling != null) {
            if (handling == FIN) {
                super.scheduleClose();
            } else {
                server.handleRequest(handling, this);
            }
        }
    }
}
