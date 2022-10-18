package ok.dht.test.monakhov;

import one.nio.config.Config;
import one.nio.http.HttpServerConfig;

import java.util.List;

@Config
public class AsyncHttpServerConfig extends HttpServerConfig {
    public int workersNumber;
    public int queueSize;

    public String selfUrl;

    public List<String> clusterUrls;
}
