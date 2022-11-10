package ok.dht.test.shakhov.http;

import ok.dht.test.shakhov.concurrent.DefaultThreadPoolManager;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

public class HttpClientFactory {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int POOL_SIZE = 1;

    private HttpClientFactory() {
    }

    public static HttpClient createHttpClient(String url) {
        String clientName = url + "-client";
        Executor executor = DefaultThreadPoolManager.createThreadPool(clientName, POOL_SIZE);

        return HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }
}
