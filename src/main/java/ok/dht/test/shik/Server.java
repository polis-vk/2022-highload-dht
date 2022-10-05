package ok.dht.test.shik;

import ok.dht.ServiceConfig;
import ok.dht.test.shik.workers.WorkersConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Basic server stub.
 */
public final class Server {

    private static final Log LOG = LogFactory.getLog(Server.class);
    private static final Path DEFAULT_DATABASE_DIR =
        Paths.get("/var/folders/85/g8ft9y9d1kb9z88s8kgjh21m0000gp/T/server");
    private static final int DEFAULT_PORT = 19234;
    private static final String DEFAULT_URL = "http://localhost:" + DEFAULT_PORT;
    private static final ServiceConfig DEFAULT_CONFIG = new ServiceConfig(
        DEFAULT_PORT,
        DEFAULT_URL,
        Collections.singletonList(DEFAULT_URL),
        DEFAULT_DATABASE_DIR
    );
    // отношение - сколько работы в селектор потоке / сколько работы в воркер потоке
    private static final double NETWORK_PUT_RATE = 0.47 / 0.32;
    private static final double NETWORK_GET_RATE = 0.47 / 0.33;
    private static final double GET_TO_PUT_RATE = 1; // в реальном приложении что-то из интервала (0, 1)
    private static final double NETWORK_RATE =
        GET_TO_PUT_RATE * NETWORK_GET_RATE + (1 - GET_TO_PUT_RATE) * NETWORK_PUT_RATE;
    private static final int MAX_WORKERS = (int) (Runtime.getRuntime().availableProcessors() * (1 + NETWORK_RATE));
    private static final long KEEP_ALIVE_TIME = 20;
    private static final WorkersConfig.QueuePolicy QUEUE_POLICY = WorkersConfig.QueuePolicy.FIFO;
    private static final int QUEUE_CAPACITY = 10000;
    private static final WorkersConfig WORKERS_CONFIG = new WorkersConfig.Builder()
        .corePoolSize(MAX_WORKERS)
        .maxPoolSize(MAX_WORKERS)
        .keepAliveTime(KEEP_ALIVE_TIME)
        .queuePolicy(QUEUE_POLICY)
        .queueCapacity(QUEUE_CAPACITY)
        .build();

    private Server() {
        // Only main method
    }

    public static void main(String[] args) throws IOException,
        ExecutionException, InterruptedException, TimeoutException {
        new ServiceImpl(DEFAULT_CONFIG, WORKERS_CONFIG).start().get(10, TimeUnit.SECONDS);
        LOG.info("Socket is ready: " + DEFAULT_URL);
    }
}
