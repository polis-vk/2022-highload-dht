package ok.dht.test.maximenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DatabaseService implements Service {
    static final Logger logger = Logger.getLogger(String.valueOf(DatabaseService.class));

    private final ServiceConfig config;
    private HttpServer server;
    private HttpServerConfig httpServerConfig;
    public DatabaseService(ServiceConfig config) throws IOException {
        this.config = config;
        httpServerConfig = createConfig(config.selfPort());
    }

    private static HttpServerConfig createConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new DatabaseHttpServer(httpServerConfig, config);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 6, week = 5)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            try {
                return new DatabaseService(config);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Can't create a service");
                return null;
            }
        }
    }
}
