package ok.dht.test.anikina;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private final ServiceConfig config;
    private DatabaseHttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new DatabaseHttpServer(config);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 2, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
