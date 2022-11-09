package ok.dht.test.gerasimov.client;

import ok.dht.test.gerasimov.sharding.Shard;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * @author Michael Gerasimov
 */
public class CircuitBreakerClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<HttpResponse<byte[]>> circuitBreaker(HttpRequest httpRequest, Shard shard) {
        if (shard.isAvailable().get()) {
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            // TODO: 09.11.2022
        }
//            } catch (InterruptedException | IOException e) {
//                if (shard.isAvailable().compareAndSet(true, false)) {
//                    scheduledExecutorService.scheduleAtFixedRate(
//                            createMonitoringNodeTask(shard),
//                            5,
//                            5,
//                            TimeUnit.NANOSECONDS
//                    );
//                }
//            }
//        }
//        return ResponseEntity.serviceUnavailable();
        return CompletableFuture.failedFuture(new RuntimeException());
    }

//    private Runnable createMonitoringNodeTask(Shard shard) {
//        return () -> {
//            try {
//                shard.getHttpClient().connect(
//                        String.format("%s:%s", shard.getHost(), shard.getPort())
//                );
//                shard.isAvailable().compareAndSet(false, true);
//                Thread.currentThread().interrupt();
//            } catch (Exception ignored) {
//                // ignored
//            }
//        };
//    }
}
