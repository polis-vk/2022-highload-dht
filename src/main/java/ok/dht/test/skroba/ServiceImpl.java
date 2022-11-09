package ok.dht.test.skroba;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.skroba.db.LevelDbEntityDao;
import ok.dht.test.skroba.server.ConcurrentHttpServer;
import ok.dht.test.skroba.shard.ManagerImpl;
import one.nio.http.HttpServer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.skroba.ServiceUtils.createConfigFromPort;

public class ServiceImpl implements Service {
    private final ServiceConfig config;
    
    private HttpServer server;
    
    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }
    
    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new ConcurrentHttpServer(new LevelDbEntityDao(config.workingDir()), new ManagerImpl(config),
                createConfigFromPort(config.selfPort()));
        server.start();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }
    
    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
