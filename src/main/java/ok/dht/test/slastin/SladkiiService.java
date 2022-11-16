package ok.dht.test.slastin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;
import org.rocksdb.Options;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class SladkiiService implements Service {
    private static final Logger log = LoggerFactory.getLogger(SladkiiService.class);

    public static final Path DEFAULT_DB_DIRECTORY = Path.of("db");
    public static final long DEFAULT_MEMTABLE_SIZE = 8 * SizeUnit.MB;

    public static final Supplier<Options> DEFAULT_OPTIONS_SUPPLIER = () -> new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(DEFAULT_MEMTABLE_SIZE)
            .setLevelCompactionDynamicLevelBytes(true);

    public static final Supplier<ExecutorService> DEFAULT_PROCESSORS_SUPPLIER = () -> {
        int cores = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cores / 2, cores,
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    };

    public static final Function<ServiceConfig, HttpServerConfig> DEFAULT_HTTP_CONFIG_MAPPER = cfg -> {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = cfg.selfPort();
        acceptor.reusePort = true;
        acceptor.threads = 2;

        HttpServerConfig httpConfig = new HttpServerConfig();
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    };

    private final ServiceConfig serviceConfig;
    private final Supplier<Options> dbOptionsSupplier;
    private final Supplier<ExecutorService> processorsSupplier;
    private final Function<ServiceConfig, HttpServerConfig> httpConfigMapper;

    private Options dbOptions;
    private SladkiiComponent component;
    private ExecutorService processors;
    private SladkiiServer server;

    private boolean isClosed;

    public SladkiiService(ServiceConfig serviceConfig) {
        this(serviceConfig, DEFAULT_OPTIONS_SUPPLIER, DEFAULT_PROCESSORS_SUPPLIER, DEFAULT_HTTP_CONFIG_MAPPER);
    }

    public SladkiiService(
            ServiceConfig serviceConfig,
            Supplier<Options> dbOptionsSupplier,
            Supplier<ExecutorService> processorsSupplier,
            Function<ServiceConfig, HttpServerConfig> httpConfigSupplier
    ) {
        this.serviceConfig = serviceConfig;
        this.dbOptionsSupplier = dbOptionsSupplier;
        this.processorsSupplier = processorsSupplier;
        this.httpConfigMapper = httpConfigSupplier;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        isClosed = false;

        dbOptions = dbOptionsSupplier.get();
        component = makeComponent(dbOptions, serviceConfig.workingDir());

        processors = processorsSupplier.get();

        server = new SladkiiServer(httpConfigMapper.apply(serviceConfig), component, processors);
        server.start();

        return CompletableFuture.completedFuture(null);
    }

    private static SladkiiComponent makeComponent(Options dbOptions, Path serverDirectory) throws IOException {
        Path location = serverDirectory.resolve(DEFAULT_DB_DIRECTORY);
        if (Files.notExists(location)) {
            Files.createDirectories(location);
        }
        return new SladkiiComponent(dbOptions, location.toString());
    }

    @Override
    public CompletableFuture<?> stop() {
        if (!isClosed) {
            // firstly close processors (before server) to give a chance to return response to the last queries
            shutdownAndAwaitTermination(processors, 1, TimeUnit.MINUTES);
            processors = null;

            server.stop();
            server = null;

            component.close();
            component = null;

            dbOptions.close();
            dbOptions = null;

            isClosed = true;
        }

        return CompletableFuture.completedFuture(null);
    }

    private static void shutdownAndAwaitTermination(ExecutorService service, int timeOut, TimeUnit unit) {
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

    @ServiceFactory(stage = 2, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new SladkiiService(config);
        }
    }
}
