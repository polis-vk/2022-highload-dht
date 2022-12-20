package ok.dht.test.shestakova;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.dao.base.Config;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DemoService implements Service {

    private final ServiceConfig config;
    private DemoHttpServer server;
    private MemorySegmentDao dao;
    private static final long FLUSH_THRESHOLD = 1 << 20; // 1 MB
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_TIME = 0L;
    private ExecutorService workersPool;
    private HttpClient httpClient;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (Files.notExists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }

        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD));
        workersPool = new ThreadPoolExecutor(
                POOL_SIZE,
                POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadFactoryBuilder()
                        .setNameFormat("WorkersPool-thread-%d")
                        .build()
        );
        httpClient = HttpClient.newBuilder()
                .executor(
                        Executors.newFixedThreadPool(
                                POOL_SIZE,
                                new ThreadFactoryBuilder()
                                        .setNameFormat("Client-thread-%d")
                                        .build()
                        ))
                .build();
        server = new DemoHttpServer(createConfigFromPort(config.selfPort()), httpClient, workersPool, config, dao);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        workersPool.shutdownNow();
        dao.close();
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

    @ServiceFactory(stage = 5, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
