package ok.dht.test.slastin.range;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;

public class RangeHttpSession extends HttpSession {
    public RangeHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof RangeResponse rangeResponse) {
            write(rangeResponse.toRangeQueueItem());
        } else {
            super.writeResponse(response, includeBody);
        }
    }
}
