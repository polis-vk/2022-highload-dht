package ok.dht.test.komissarov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.komissarov.utils.CustomHttpServer;
import one.nio.http.HttpServer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class CourseService implements Service {

    private final ServiceConfig config;
    private HttpServer server;

    public CourseService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new CustomHttpServer(config);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 3, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new CourseService(config);
        }

    }

}
