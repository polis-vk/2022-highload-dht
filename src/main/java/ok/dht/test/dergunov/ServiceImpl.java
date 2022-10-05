package ok.dht.test.dergunov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.dergunov.database.Config;
import ok.dht.test.dergunov.database.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class ServiceImpl implements Service {

    public static final long DEFAULT_FLUSH_THRESHOLD_BYTES = 4194304; // 4 MB
    private HttpServer server;
    private final ServiceConfig config;
    private MemorySegmentDao database;

    private final long flushThresholdBytes;

    ServiceImpl(ServiceConfig config, long flushThresholdBytes) {
        this.config = config;
        this.flushThresholdBytes = flushThresholdBytes;
    }

    ServiceImpl(ServiceConfig config) {
        this(config, DEFAULT_FLUSH_THRESHOLD_BYTES);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        database.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        database = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        server = new HttpServerImpl(createConfigFromPort(config.selfPort()), database);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 2, week = 1)
    public static class ServiceFactoryImpl implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }

}
