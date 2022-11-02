package ok.dht.test.armenakyan.util;

import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;

public final class ServiceUtils {
    public static final String TIMESTAMP_HEADER = "Coordinator-Timestamp";
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    
    private ServiceUtils() {
        // utility class
    }

    public static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            session.close();
        }
    }
}
