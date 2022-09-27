package ok.dht.test.dergunov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    private final ServiceConfig config;
    private final HttpServer server;

    public ServiceImpl(ServiceConfig config) throws IOException {
        this.config = config;
        this.server = new HttpServer(createConfigFromPort(config.selfPort()));
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server.start();
        return null;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return null;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class ServiceFactoryImpl implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            try {
                return new ServiceImpl(config);
            } catch (IOException suppressedIO) {
                throw new RuntimeException(suppressedIO);
            }
        }
    }
}
