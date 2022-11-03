package ok.dht.test.kazakov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kazakov.dao.Config;
import ok.dht.test.kazakov.dao.MemorySegmentDao;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.http.InternalHttpClient;
import ok.dht.test.kazakov.service.sharding.ConsistentHashingShardDeterminer;
import ok.dht.test.kazakov.service.sharding.Shard;
import ok.dht.test.kazakov.service.sharding.ShardDeterminer;
import ok.dht.test.kazakov.service.validation.DaoRequestsValidatorBuilder;
import ok.dht.test.kazakov.service.ws.DaoWebService;
import ok.dht.test.kazakov.service.ws.InternalDaoWebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ServiceConfigurer implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceConfigurer.class);

    private static final int INTERNAL_HTTP_CLIENT_THREADS = Runtime.getRuntime().availableProcessors() * 16;
    private static final int INTERNAL_HTTP_CLIENT_QUEUE_CAPACITY = INTERNAL_HTTP_CLIENT_THREADS;
    private static final int WORKER_POOL_THREADS = Runtime.getRuntime().availableProcessors() * 8;
    private static final int WORKER_POOL_QUEUE_CAPACITY = WORKER_POOL_THREADS;
    private static final int FLUSH_THRESHOLD_BYTES = 2 * 1024 * 1024;
    private static final Path DAO_DATA_RELATIVE_PATH = Path.of("dao");
    private static final Path LAMPORT_CLOCK_DATA_RELATIVE_PATH = Path.of("clock.bin");

    private final ServiceConfig config;
    private final Clock clock;

    private volatile ExecutorService asyncExecutor;
    private volatile DaoHttpServer server;
    private volatile DaoService daoService;
    private volatile LamportClock lamportClock;

    public ServiceConfigurer(@Nonnull final ServiceConfig config,
                             @Nonnull final Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<?> start() {
        if (asyncExecutor == null) {
            synchronized (this) {
                if (asyncExecutor == null) {
                    asyncExecutor = DaoExecutorServiceHelper.createDiscardOldestThreadPool(
                            "DaoAsyncExecutor",
                            WORKER_POOL_THREADS,
                            WORKER_POOL_QUEUE_CAPACITY
                    );
                }
            }
        }

        return CompletableFuture.runAsync(() -> {
            final long measureTimeFrom = clock.millis();
            try {
                createAllMissingDirectories(config.workingDir());
                server = new DaoHttpServer(
                        DaoHttpServer.createConfigFromPort(config.selfPort()),
                        asyncExecutor,
                        this
                );
                configureDaoWebService(server);

                server.start();
            } catch (final IOException e) {
                LOG.error("Unexpected IOException during DaoWebService.start()", e);
                throw new UncheckedIOException(e);
            }

            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService started in {}ms at {}", measureTimeTo - measureTimeFrom, config.selfUrl());
        }, asyncExecutor);
    }

    private void createAllMissingDirectories(@Nonnull final Path workingDir) throws IOException {
        Files.createDirectories(workingDir.resolve(DAO_DATA_RELATIVE_PATH));
    }

    private void configureDaoWebService(@Nonnull final DaoHttpServer server) throws IOException {
        final MemorySegmentDao dao = new MemorySegmentDao(
                new Config(config.workingDir().resolve(DAO_DATA_RELATIVE_PATH), FLUSH_THRESHOLD_BYTES)
        );
        daoService = new DaoService(dao);
        final DaoRequestsValidatorBuilder daoRequestsValidatorBuilder = new DaoRequestsValidatorBuilder();

        final List<String> clusterUrls = new ArrayList<>(config.clusterUrls());
        Collections.sort(clusterUrls);

        final List<Shard> shards = new ArrayList<>(clusterUrls.size());
        for (int i = 0; i < clusterUrls.size(); i++) {
            final String url = clusterUrls.get(i);
            shards.add(new Shard(url, url.equals(config.selfUrl()), i));
        }
        final ShardDeterminer<String> shardDeterminer = new ConsistentHashingShardDeterminer<>(shards);

        final InternalHttpClient internalHttpClient = new InternalHttpClient(
                DaoExecutorServiceHelper.createDiscardOldestThreadPool(
                        "InternalHttpClientExecutor",
                        INTERNAL_HTTP_CLIENT_THREADS,
                        INTERNAL_HTTP_CLIENT_QUEUE_CAPACITY
                )
        );

        lamportClock = LamportClock.loadFrom(config.workingDir().resolve(LAMPORT_CLOCK_DATA_RELATIVE_PATH));

        new DaoWebService(daoService, daoRequestsValidatorBuilder, shardDeterminer, internalHttpClient, lamportClock)
                .configure(server);

        new InternalDaoWebService(daoService, lamportClock)
                .configure(server);
    }

    @Override
    public CompletableFuture<?> stop() {
        if (asyncExecutor == null) {
            LOG.warn("DaoWebService is already stopped");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            final long measureTimeFrom = clock.millis();
            try {
                asyncExecutor.shutdownNow();
                server.stop();
                daoService.close();
                lamportClock.close();
                asyncExecutor = null;
            } catch (final IOException e) {
                LOG.error("Unexpected IOException during DaoWebService.stop()", e);
                throw new UncheckedIOException(e);
            }

            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService stopped in {}ms", measureTimeTo - measureTimeFrom);
        }, asyncExecutor);
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(@Nonnull final ServiceConfig config) {
            return new ServiceConfigurer(config, Clock.systemUTC());
        }
    }
}
