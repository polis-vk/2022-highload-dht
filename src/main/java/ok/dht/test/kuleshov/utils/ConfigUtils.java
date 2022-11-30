package ok.dht.test.kuleshov.utils;

import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

public final class ConfigUtils {
    private ConfigUtils() {

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
