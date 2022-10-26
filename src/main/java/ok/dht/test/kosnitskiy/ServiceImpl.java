package ok.dht.test.kosnitskiy;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kosnitskiy.dao.Config;
import ok.dht.test.kosnitskiy.dao.MemorySegmentDao;
import ok.dht.test.kosnitskiy.server.HttpServerImpl;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    private static final int IN_MEMORY_SIZE = 8388608;

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final long KEEP_ALIVE_SECS = 60L;
    private static final int MAX_QUEUE_SIZE = MAX_POOL_SIZE * 40;

    private final ServiceConfig config;
    private HttpServerImpl server;
    private MemorySegmentDao memorySegmentDao;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        memorySegmentDao = new MemorySegmentDao(new Config(config.workingDir(), IN_MEMORY_SIZE));
        server = new HttpServerImpl(createConfigFromPort(config.selfPort()), memorySegmentDao,
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        MAX_POOL_SIZE,
                        KEEP_ALIVE_SECS,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<Runnable>(MAX_QUEUE_SIZE)
                ));
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        memorySegmentDao.close();
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
