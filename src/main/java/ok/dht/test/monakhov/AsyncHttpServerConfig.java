package ok.dht.test.monakhov;

import one.nio.config.Config;
import one.nio.http.HttpServerConfig;

@Config
public class AsyncHttpServerConfig extends HttpServerConfig {
    public int workersNumber;
    public int queueSize;

    public AsyncHttpServerConfig(int workersNumber, int queueSize) {
        this.workersNumber = workersNumber;
        this.queueSize = queueSize;
    }
}
