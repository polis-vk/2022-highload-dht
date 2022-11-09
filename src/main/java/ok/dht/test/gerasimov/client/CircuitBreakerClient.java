package ok.dht.test.gerasimov.client;

import ok.dht.test.gerasimov.sharding.Shard;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class CircuitBreakerClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<HttpResponse<byte[]>> circuitBreaker(HttpRequest httpRequest, Shard shard) {
        if (shard.isAvailable().get()) {
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            // TODO: 10.11.2022 Прикрутить circuitBreaker к новым HttpResponse
        }
        return CompletableFuture.failedFuture(new RuntimeException());
    }
}
