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
import ok.dht.test.kazakov.service.validation.EntityRequestsValidatorBuilder;
import ok.dht.test.kazakov.service.validation.RangeRequestsValidatorBuilder;
import ok.dht.test.kazakov.service.ws.EntityWebService;
import ok.dht.test.kazakov.service.ws.InternalDaoWebService;
import ok.dht.test.kazakov.service.ws.RangeWebService;
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
import java.util.concurrent.Executors;

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
    private volatile ExecutorService internalHttpExecutor;
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
        final long measureTimeFrom = clock.millis();
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

        final ShardDeterminer<String> shardDeterminer = createShardDeterminer();

        internalHttpExecutor = DaoExecutorServiceHelper.createDiscardOldestThreadPool(
                "InternalHttpClientExecutor",
                INTERNAL_HTTP_CLIENT_THREADS,
                INTERNAL_HTTP_CLIENT_QUEUE_CAPACITY
        );
        final InternalHttpClient internalHttpClient = new InternalHttpClient(internalHttpExecutor);

        lamportClock = LamportClock.loadFrom(config.workingDir().resolve(LAMPORT_CLOCK_DATA_RELATIVE_PATH));

        final EntityRequestsValidatorBuilder entityRequestsValidatorBuilder = new EntityRequestsValidatorBuilder();
        new EntityWebService(
                daoService,
                entityRequestsValidatorBuilder,
                shardDeterminer,
                internalHttpClient,
                lamportClock
        )
                .configure(server);

        new InternalDaoWebService(daoService, lamportClock)
                .configure(server);

        final RangeRequestsValidatorBuilder rangeRequestsValidatorBuilder = new RangeRequestsValidatorBuilder();
        new RangeWebService(daoService, rangeRequestsValidatorBuilder)
                .configure(server);
    }

    private ShardDeterminer<String> createShardDeterminer() {
        final List<String> clusterUrls = new ArrayList<>(config.clusterUrls());
        Collections.sort(clusterUrls);

        final List<Shard> shards = new ArrayList<>(clusterUrls.size());
        for (int i = 0; i < clusterUrls.size(); i++) {
            final String url = clusterUrls.get(i);
            shards.add(new Shard(url, url.equals(config.selfUrl()), i));
        }
        return new ConsistentHashingShardDeterminer<>(shards);
    }

    @Override
    public CompletableFuture<?> stop() {
        final long measureTimeFrom = clock.millis();
        if (asyncExecutor == null) {
            LOG.warn("DaoWebService is already stopped");
            return CompletableFuture.completedFuture(null);
        }

        final ExecutorService stopExecutor = Executors.newSingleThreadExecutor();

        return CompletableFuture.runAsync(() -> {
            Exception exception = ExceptionUtils.tryExecute(null, stopExecutor::shutdown);
            exception = ExceptionUtils.tryExecute(
                    exception,
                    () -> DaoExecutorServiceHelper.shutdownGracefully(asyncExecutor)
            );
            exception = ExceptionUtils.tryExecute(
                    exception,
                    () -> DaoExecutorServiceHelper.shutdownGracefully(internalHttpExecutor)
            );
            exception = ExceptionUtils.tryExecute(exception, server::stop);
            exception = ExceptionUtils.tryExecute(exception, daoService::close);
            exception = ExceptionUtils.tryExecute(exception, lamportClock::close);

            asyncExecutor = null;
            internalHttpExecutor = null;
            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService stopped in {}ms", measureTimeTo - measureTimeFrom);

            if (exception != null) {
                LOG.error("Unexpected exception during DaoWebService.stop()", exception);
            }
        }, stopExecutor);
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(@Nonnull final ServiceConfig config) {
            return new ServiceConfigurer(config, Clock.systemUTC());
        }
    }
}
