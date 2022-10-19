package ok.dht.test.skroba;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MyServiceImpl implements Service {
    private final ServiceConfig config;
    
    private HttpServer server;
    
    public MyServiceImpl(ServiceConfig config) {
        this.config = config;
    }
    
    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new MyConcurrentHttpServer(config, MyServiceUtils.createConfigFromPort(config.selfPort()));
        server.start();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }
    
    @ServiceFactory(stage = 3, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        
        @Override
        public Service create(ServiceConfig config) {
            return new MyServiceImpl(config);
        }
    }
}
