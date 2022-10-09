package ok.dht.test.ushkov.http;

import one.nio.http.HttpServerConfig;

public class AsyncHttpServerConfig extends HttpServerConfig {
    public int workers;
    public int queueCapacity;
}
