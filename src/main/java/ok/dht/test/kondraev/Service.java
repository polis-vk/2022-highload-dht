package ok.dht.test.kondraev;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kondraev.dao.Dao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class Service implements ok.dht.Service {
    private static final long FLUSH_THRESHOLD_BYTES = 1 << 20;
    private final ServiceConfig config;
    private HttpServer server;
    private Dao dao;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> start() throws IOException {
        try {
            Files.createDirectory(config.workingDir());
        } catch (FileAlreadyExistsException ignored) {
            // directory exists, nothing to do
        }
        dao = Dao.of(FLUSH_THRESHOLD_BYTES, config.workingDir());
        server = new Server(createConfig(config.selfPort()), dao);
        return CompletableFuture.runAsync(server::start);
    }

    @Override
    public CompletableFuture<Void> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 2)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new Service(config);
        }
    }
}
