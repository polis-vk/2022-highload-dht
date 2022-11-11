package ok.dht.test.ilin.config;

import one.nio.http.HttpServerConfig;

public class ExpandableHttpServerConfig extends HttpServerConfig {
    public int workers;
    public int queueCapacity;
    public static final int DEFAULT_NUMBER_OF_WORKERS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 256;

    public ExpandableHttpServerConfig(int workers, int queueCapacity) {
        this.workers = workers;
        this.queueCapacity = queueCapacity;
    }

    public ExpandableHttpServerConfig() {
        this.workers = DEFAULT_NUMBER_OF_WORKERS;
        this.queueCapacity = DEFAULT_QUEUE_CAPACITY;
    }
}
