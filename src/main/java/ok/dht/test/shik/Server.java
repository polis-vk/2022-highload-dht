package ok.dht.test.shik;

import ok.dht.ServiceConfig;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.workers.WorkersConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Basic server stub.
 */
public final class Server {

    private static final Log LOG = LogFactory.getLog(Server.class);
    private static final Path DEFAULT_DATABASE_DIR =
        Paths.get("/var/folders/85/g8ft9y9d1kb9z88s8kgjh21m0000gp/T/server1");
    private static final int DEFAULT_PORT1 = 19234;
    private static final int DEFAULT_PORT2 = 19876;
    private static final int DEFAULT_PORT3 = 19877;
    private static final String LOCALHOST = "http://localhost:";
    private static final String DEFAULT_URL1 = LOCALHOST + DEFAULT_PORT1;
    private static final String DEFAULT_URL2 = LOCALHOST + DEFAULT_PORT2;
    private static final String DEFAULT_URL3 = LOCALHOST + DEFAULT_PORT3;
    private static final ServiceConfig DEFAULT_CONFIG1 = new ServiceConfig(
        DEFAULT_PORT1,
        DEFAULT_URL1,
        List.of(
            DEFAULT_URL1,
            DEFAULT_URL2,
            DEFAULT_URL3
        ),
        DEFAULT_DATABASE_DIR
    );
    private static final int MAX_WORKERS = 8;
    private static final long KEEP_ALIVE_TIME = 20;
    private static final WorkersConfig.QueuePolicy QUEUE_POLICY = WorkersConfig.QueuePolicy.FIFO;
    private static final int QUEUE_CAPACITY = 100;
    private static final WorkersConfig WORKERS_CONFIG = new WorkersConfig.Builder()
        .corePoolSize(0)
        .maxPoolSize(MAX_WORKERS)
        .keepAliveTime(KEEP_ALIVE_TIME)
        .queuePolicy(QUEUE_POLICY)
        .queueCapacity(QUEUE_CAPACITY)
        .build();
    private static final int V_NODES_NUMBER = 50;
    private static final ShardingConfig SHARDING_CONFIG = new ShardingConfig(V_NODES_NUMBER);
    private static final int TIMEOUT_SECONDS = 10;

    private Server() {
        // Only main method
    }

    public static void main(String[] args) throws IOException,
        ExecutionException, InterruptedException, TimeoutException {
        new ServiceImpl(DEFAULT_CONFIG1, WORKERS_CONFIG, SHARDING_CONFIG)
            .start().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        LOG.info("Socket is ready: " + DEFAULT_URL1);
    }
}
