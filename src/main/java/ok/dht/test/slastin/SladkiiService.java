package ok.dht.test.slastin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.slastin.sharding.ConsistentHashingManager;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.async.CustomThreadFactory;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;
import one.nio.util.Hash;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SladkiiService implements Service {
    private static final Logger log = LoggerFactory.getLogger(SladkiiService.class);

    public static final Path DEFAULT_DB_DIRECTORY = Path.of("db");
    public static final long DEFAULT_MEMTABLE_SIZE = 8 * SizeUnit.MB;

    public static final Supplier<Options> DEFAULT_OPTIONS_SUPPLIER = () -> new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(DEFAULT_MEMTABLE_SIZE)
            .setLevelCompactionDynamicLevelBytes(true);

    public static final Supplier<Options> DEFAULT_OPTIONS_WITH_BLOOM_SUPPLIER = () -> {
        var tableConfig = new BlockBasedTableConfig().setFilterPolicy(new BloomFilter(10.0, false));
        return DEFAULT_OPTIONS_SUPPLIER.get().setTableFormatConfig(tableConfig);
    };

    private final ServiceConfig serviceConfig;
    private final Supplier<Options> dbOptionsSupplier;
    private final Supplier<ExecutorService> heavyExecutorSupplier;
    private final Supplier<ExecutorService> lightExecutorSupplier;
    private final Supplier<ExecutorService> httpClientExecutorSupplier;
    private final Supplier<ShardingManager> shardingManagerSupplier;
    private final Supplier<HttpServerConfig> httpServerConfigSupplier;

    private Options dbOptions;
    private SladkiiComponent component;
    private ExecutorService heavyExecutor;
    private ExecutorService lightExecutor;
    private ExecutorService httpClientExecutor;
    private ShardingManager shardingManager;
    private SladkiiServer server;

    private boolean isClosed;

    static {
        // to enable bloom filter and more rocksdb features
        RocksDB.loadLibrary();
    }

    public static Supplier<HttpServerConfig> makeDefaultHttpServerConfigSupplier(ServiceConfig serviceConfig) {
        return () -> {
            AcceptorConfig acceptor = new AcceptorConfig();
            acceptor.port = serviceConfig.selfPort();
            acceptor.reusePort = true;
            acceptor.threads = 2;

            HttpServerConfig httpConfig = new HttpServerConfig();
            httpConfig.acceptors = new AcceptorConfig[]{acceptor};
            return httpConfig;
        };
    }

    public static Supplier<ExecutorService> makeDefaultHeavyExecutorSupplier(ServiceConfig serviceConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        int cnt = Math.max(1, (int) Math.ceil(cores * 0.5));

        return () -> new ThreadPoolExecutor(
                cnt, cnt,
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                new CustomThreadFactory(serviceConfig.selfUrl() + "-heavy")
        );
    }

    public static Supplier<ExecutorService> makeDefaultLightExecutorSupplier(ServiceConfig serviceConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        int from = Math.max(1, (int) Math.ceil(cores * 0.25));
        int to = Math.max(1, (int) Math.ceil(cores * 0.5));

        return () -> new ThreadPoolExecutor(
                from, to,
                10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2048),
                new CustomThreadFactory(serviceConfig.selfUrl() + "-light")
        );
    }

    public static Supplier<ExecutorService> makeDefaultHttpClientExecutorSupplier(ServiceConfig serviceConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        int from = Math.max(1, (int) Math.ceil(cores * 0.25));
        int to = Math.max(1, (int) Math.ceil(cores * 0.5));

        return () -> new ThreadPoolExecutor(
                from, to,
                10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                new CustomThreadFactory(serviceConfig.selfUrl() + "-httpclient")
        );
    }

    public static Supplier<ShardingManager> makeDefaultShardingManagerSupplier(ServiceConfig serviceConfig) {
        return () -> new ConsistentHashingManager(serviceConfig.clusterUrls(), 50, Hash::murmur3);
    }

    public SladkiiService(ServiceConfig serviceConfig) {
        this(serviceConfig,
                DEFAULT_OPTIONS_SUPPLIER,
                makeDefaultHeavyExecutorSupplier(serviceConfig),
                makeDefaultLightExecutorSupplier(serviceConfig),
                makeDefaultHttpClientExecutorSupplier(serviceConfig),
                makeDefaultShardingManagerSupplier(serviceConfig),
                makeDefaultHttpServerConfigSupplier(serviceConfig)
        );
    }

    public SladkiiService(
            ServiceConfig serviceConfig,
            Supplier<Options> dbOptionsSupplier,
            Supplier<ExecutorService> heavyExecutorSupplier,
            Supplier<ExecutorService> lightExecutorSupplier,
            Supplier<ExecutorService> httpclientExecutorSupplier,
            Supplier<ShardingManager> shardingManagerSupplier,
            Supplier<HttpServerConfig> httpServerConfigSupplier
    ) {
        this.serviceConfig = serviceConfig;
        this.dbOptionsSupplier = dbOptionsSupplier;
        this.heavyExecutorSupplier = heavyExecutorSupplier;
        this.lightExecutorSupplier = lightExecutorSupplier;
        this.httpClientExecutorSupplier = httpclientExecutorSupplier;
        this.shardingManagerSupplier = shardingManagerSupplier;
        this.httpServerConfigSupplier = httpServerConfigSupplier;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        isClosed = false;

        dbOptions = dbOptionsSupplier.get();
        component = makeSladkiiComponent();

        heavyExecutor = heavyExecutorSupplier.get();
        lightExecutor = lightExecutorSupplier.get();
        httpClientExecutor = httpClientExecutorSupplier.get();

        shardingManager = shardingManagerSupplier.get();

        server = new SladkiiServer(
                httpServerConfigSupplier.get(), serviceConfig, component, shardingManager,
                heavyExecutor, lightExecutor, httpClientExecutor
        );
        server.start();

        return CompletableFuture.completedFuture(null);
    }

    Path getDbLocation() {
        return serviceConfig.workingDir()
                .resolve("node" + serviceConfig.clusterUrls().indexOf(serviceConfig.selfUrl()))
                .resolve(DEFAULT_DB_DIRECTORY);
    }

    private SladkiiComponent makeSladkiiComponent() throws IOException {
        Path dbLocation = getDbLocation();

        if (Files.notExists(dbLocation)) {
            Files.createDirectories(dbLocation);
        }

        return new SladkiiComponent(dbOptions, dbLocation.toString());
    }

    @Override
    public CompletableFuture<?> stop() {
        if (!isClosed) {
            // firstly close executors (before server) to give a chance to return response to the last queries

            gracefulShutdown(lightExecutor, 30, TimeUnit.SECONDS);
            lightExecutor = null;

            gracefulShutdown(httpClientExecutor, 30, TimeUnit.SECONDS);
            httpClientExecutor = null;

            gracefulShutdown(heavyExecutor, 30, TimeUnit.SECONDS);
            heavyExecutor = null;

            server.stop();
            server = null;

            shardingManager = null;

            component.close();
            component = null;

            dbOptions.close();
            dbOptions = null;

            isClosed = true;
        }

        return CompletableFuture.completedFuture(null);
    }

    private static void gracefulShutdown(ExecutorService service, int timeOut, TimeUnit unit) {
        service.shutdown();
        try {
            if (!service.awaitTermination(timeOut, unit)) {
                service.shutdownNow();
                if (!service.awaitTermination(timeOut, unit)) {
                    log.error("Can not terminal all workers");
                }
            }
        } catch (final InterruptedException ie) {
            log.warn("Service was interrupted during shutdown");
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @ServiceFactory(stage = 5, week = 5, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new SladkiiService(config);
        }
    }
}
