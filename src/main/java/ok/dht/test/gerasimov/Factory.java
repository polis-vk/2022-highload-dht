package ok.dht.test.gerasimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.gerasimov.exception.EntityServiceException;
import ok.dht.test.gerasimov.server.Server;
import ok.dht.test.gerasimov.service.HandleService;
import ok.dht.test.gerasimov.service.StartStopService;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.ConsistentHashImpl;
import ok.dht.test.gerasimov.sharding.Shard;
import ok.dht.test.gerasimov.sharding.VNode;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;
import one.nio.util.Hash;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

@ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
public class Factory implements ServiceFactory.Factory {
    private static final int NUMBER_VIRTUAL_NODES_PER_SHARD = 3;
    private static final int SELECTOR_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int DEFAULT_THREAD_POOL_SIZE = 32;
    private static final int KEEP_A_LIVE_TIME_IN_NANOSECONDS = 0;
    private static final int WORK_QUEUE_CAPACITY = 256;

    public static DB createDao(Path path) throws IOException {
        try {
            return factory.open(new File(path.toString()), new Options());
        } catch (IOException e) {
            throw new EntityServiceException("Can not create DAO", e);
        }
    }

    public static HttpServer createHttpServer(ServiceConfig serviceConfig, Map<String, HandleService> services) {
        try {
            HttpServerConfig httpServerConfig = new HttpServerConfig();
            AcceptorConfig acceptor = new AcceptorConfig();

            acceptor.port = serviceConfig.selfPort();
            acceptor.reusePort = true;
            httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};
            httpServerConfig.selectors = SELECTOR_POOL_SIZE;

            return new Server(
                    httpServerConfig,
                    services,
                    new ThreadPoolExecutor(
                            DEFAULT_THREAD_POOL_SIZE,
                            DEFAULT_THREAD_POOL_SIZE,
                            KEEP_A_LIVE_TIME_IN_NANOSECONDS,
                            TimeUnit.NANOSECONDS,
                            new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY)
                    ),
                    new ScheduledThreadPoolExecutor(serviceConfig.clusterUrls().size())
            );
        } catch (IOException e) {
            throw new EntityServiceException("Can not create HttpServer", e);
        }
    }

    public static ConsistentHash<String> createConsistentHash(ServiceConfig serviceConfig) {
        List<Shard> shards = new ArrayList<>();
        for (int i = 0; i < serviceConfig.clusterUrls().size(); i++) {
            shards.add(new Shard(serviceConfig.clusterUrls().get(i), i));
        }

        List<VNode> vnodes = new ArrayList<>();
        for (Shard shard : shards) {
            for (int i = 0; i < NUMBER_VIRTUAL_NODES_PER_SHARD; i++) {
                int hashcode = Hash.murmur3(shard.getHost() + shard.getPort() + i);
                vnodes.add(new VNode(shard, hashcode));
            }
        }
        vnodes.sort(VNode::compareTo);
        return new ConsistentHashImpl<>(vnodes, shards);
    }

    @Override
    public Service create(ServiceConfig config) {
        return new StartStopService(config);
    }
}
