package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.hintedhandoff.HintsClient;
import ok.dht.test.siniachenko.hintedhandoff.HintsManager;
import ok.dht.test.siniachenko.hintedhandoff.InMemoryHintsManager;
import ok.dht.test.siniachenko.nodetaskmanager.NodeTaskManager;
import ok.dht.test.siniachenko.range.ChunkedTransferEncoder;
import ok.dht.test.siniachenko.range.RangeService;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.DbImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.concurrent.*;

public class TycoonService implements ok.dht.Service {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final int THREAD_POOL_QUEUE_CAPACITY = 128;

    private final ServiceConfig config;
    private DB levelDb;
    private TycoonHttpServer server;
    private ExecutorService executorService;

    public TycoonService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        // DB
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
        LOG.info("Started DB in directory {}", config.workingDir());

        // Executor
        int threadPoolSize = AVAILABLE_PROCESSORS;
        executorService = new ThreadPoolExecutor(
            threadPoolSize, threadPoolSize,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY)
        );

        // Node teaks manager
        NodeTaskManager nodeTaskManager = new NodeTaskManager(
            executorService, config.clusterUrls(),
            THREAD_POOL_QUEUE_CAPACITY, AVAILABLE_PROCESSORS / 2
        );

        // Chunked Transfer Encoder
        ChunkedTransferEncoder chunkedTransferEncoder = new ChunkedTransferEncoder();

        // HTTP Client
        HttpClient httpClient = HttpClient.newHttpClient();

        // Hints Manager
        HintsManager hintsManager = new InMemoryHintsManager(chunkedTransferEncoder);

        // Hints Client
        HintsClient hintsClient = new HintsClient(config, levelDb, httpClient, executorService);
        try {
            fetchHintsFromAllReplicas(hintsClient);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        // Http Server
        server = new TycoonHttpServer(
            config.selfPort(),
            executorService,
            new EntityServiceCoordinator(
                config, levelDb,
                executorService, httpClient,
                nodeTaskManager, hintsManager
            ),
            new EntityServiceReplica(levelDb),
            new RangeService(levelDb, chunkedTransferEncoder),
            hintsManager
        );
        server.start();
        LOG.info("Service started on {}, executor threads: {}", config.selfUrl(), threadPoolSize);

        return CompletableFuture.completedFuture(null);
    }

    private void fetchHintsFromAllReplicas(HintsClient hintsClient) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(config.clusterUrls().size() - 1);
        for (String replicaUrl : config.clusterUrls()) {
            if (!replicaUrl.equals(config.selfUrl())) {
                hintsClient.fetchHintsFromReplica(replicaUrl, countDownLatch);
            }
        }
        countDownLatch.await();
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (levelDb != null) {
            levelDb.close();
        }

        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 7, week = 1, bonuses = "SingleNodeTest#respectFileFolder,HintedHandoffTest#oneFailedReplicaOneKey")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
