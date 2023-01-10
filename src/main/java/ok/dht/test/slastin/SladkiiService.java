package ok.dht.test.slastin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.slastin.node.Node;
import ok.dht.test.slastin.node.NodeConfig;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final Supplier<ExecutorService> processorsSupplier;
    private final Supplier<ShardingManager> shardingManagerSupplier;
    private final Supplier<HttpServerConfig> httpServerConfigSupplier;
    private final List<NodeConfig> nodeConfigs;

    private Options dbOptions;
    private SladkiiComponent component;
    private ExecutorService processors;
    private ShardingManager shardingManager;
    private List<Node> nodes;
    private SladkiiServer server;

    private boolean isClosed;

    static {
        RocksDB.loadLibrary(); // to enable bloom filter and more rocksdb features
    }

    public static Supplier<ExecutorService> makeDefaultProcessorsSupplier(ServiceConfig serviceConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        return () -> new ThreadPoolExecutor(
                cores / 2, cores,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new CustomThreadFactory(serviceConfig.selfUrl() + "-processor")
        );
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

    public static Supplier<ShardingManager> makeDefaultShardingManagerSupplier(ServiceConfig serviceConfig) {
        return () -> new ConsistentHashingManager(serviceConfig.clusterUrls(), 50, Hash::murmur3);
    }

    public static List<NodeConfig> makeDefaultNodeConfigs(ServiceConfig serviceConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        return serviceConfig.clusterUrls().stream()
                .map(url -> new NodeConfig(512, cores / 2))
                .toList();
    }

    public SladkiiService(ServiceConfig serviceConfig) {
        this(serviceConfig,
                DEFAULT_OPTIONS_SUPPLIER,
                makeDefaultProcessorsSupplier(serviceConfig),
                makeDefaultShardingManagerSupplier(serviceConfig),
                makeDefaultHttpServerConfigSupplier(serviceConfig),
                makeDefaultNodeConfigs(serviceConfig)
        );
    }

    public SladkiiService(
            ServiceConfig serviceConfig,
            Supplier<Options> dbOptionsSupplier,
            Supplier<ExecutorService> processorsSupplier,
            Supplier<ShardingManager> shardingManagerSupplier,
            Supplier<HttpServerConfig> httpServerConfigSupplier,
            List<NodeConfig> nodeConfigs
    ) {
        this.serviceConfig = serviceConfig;
        this.dbOptionsSupplier = dbOptionsSupplier;
        this.processorsSupplier = processorsSupplier;
        this.shardingManagerSupplier = shardingManagerSupplier;
        this.httpServerConfigSupplier = httpServerConfigSupplier;
        this.nodeConfigs = nodeConfigs;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        isClosed = false;

        dbOptions = dbOptionsSupplier.get();
        component = makeSladkiiComponent();

        processors = processorsSupplier.get();

        shardingManager = shardingManagerSupplier.get();

        nodes = nodeConfigs.stream().map(Node::new).toList();

        server = new SladkiiServer(
                httpServerConfigSupplier.get(), serviceConfig, nodes, component, processors, shardingManager
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
            // firstly close processors (before server) to give a chance to return response to the last queries
            gracefulShutdown(processors, 1, TimeUnit.MINUTES);
            processors = null;

            server.stop();
            server = null;

            nodes = null;

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

    @ServiceFactory(stage = 4, week = 4, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new SladkiiService(config);
        }
    }
}
