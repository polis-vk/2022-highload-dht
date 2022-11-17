package ok.dht.test.kiselyov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kiselyov.dao.Config;
import ok.dht.test.kiselyov.dao.impl.PersistentDao;
import ok.dht.test.kiselyov.util.CustomLinkedBlockingDeque;
import one.nio.http.HttpServer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private PersistentDao dao;
    private static final int FLUSH_THRESHOLD_BYTES = 1 << 20;
    private ExecutorService executorService;
    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = MAXIMUM_POOL_SIZE > 2 ? MAXIMUM_POOL_SIZE - 2 : MAXIMUM_POOL_SIZE;
    private static final int DEQUE_CAPACITY = 256;

    public WebService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (Files.notExists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }
        dao = new PersistentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        executorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, 0L,
                TimeUnit.MILLISECONDS, new CustomLinkedBlockingDeque<>(DEQUE_CAPACITY));
        server = new DaoHttpServer(config, executorService, dao);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        executorService.shutdownNow();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new WebService(config);
        }
    }
}
