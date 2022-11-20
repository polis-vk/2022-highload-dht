package ok.dht.test.ushkov;

import one.nio.http.HttpServerConfig;

public class RocksDBHttpServerConfig extends HttpServerConfig {
    public int workers;
    public int queueCapacity;
}
