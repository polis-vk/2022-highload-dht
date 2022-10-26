package ok.dht.test.kurdyukov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import ok.dht.test.kurdyukov.http.HttpShardServer;
import ok.dht.test.kurdyukov.sharding.ConsistentHashingSharding;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.ServerConfig;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    public static final int THREAD_POOL_SIZE = 7;
    public static final int SELECTOR_SIZE = 4;

    private final ServiceConfig serviceConfig;

    private HttpServer httpServer;

    public ServiceImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        httpServer = createHttpServer(
                serviceConfig.selfPort(),
                serviceConfig.selfUrl(),
                serviceConfig.clusterUrls(),
                createDao(serviceConfig.workingDir())
        );

        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();

        return CompletableFuture.completedFuture(null);
    }

    private static DB createDao(
            java.nio.file.Path workingDir
    ) throws IOException {
        Options options = new Options();

        return factory.open(new File(workingDir.toString()), options);
    }

    private static HttpServer createHttpServer(
            int port,
            String url,
            List<String> urls,
            DB levelDB
    ) throws IOException {
        return new HttpShardServer(
                httpServerConfig(
                        port,
                        url
                ),
                urls,
                new DaoRepository(levelDB),
                new ThreadPoolExecutor(
                        THREAD_POOL_SIZE, // fixed thread pool
                        THREAD_POOL_SIZE,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(THREAD_POOL_SIZE * 4)
                ),
                new ConsistentHashingSharding(urls, 5)
        );
    }

    private static HttpServerConfig httpServerConfig(
            int port,
            String url
    ) {
        ServerConfig serverConfig = ServerConfig.from(url);

        HttpServerConfig httpConfig = new HttpServerConfig();

        httpConfig.acceptors = serverConfig.acceptors;
        httpConfig.selectors = SELECTOR_SIZE;

        for (var acceptor : httpConfig.acceptors) {
            acceptor.reusePort = true;
            acceptor.port = port;
        }

        return httpConfig;
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
