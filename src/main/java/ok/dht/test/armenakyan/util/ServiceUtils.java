package ok.dht.test.armenakyan.util;

import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

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
}
