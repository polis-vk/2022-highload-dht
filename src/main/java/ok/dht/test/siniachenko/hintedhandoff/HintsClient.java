package ok.dht.test.siniachenko.hintedhandoff;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.TycoonHttpServer;
import org.iq80.leveldb.DB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;

import static ok.dht.test.siniachenko.TycoonHttpServer.REPLICA_URL_HEADER;

public class HintsClient {
    private final ServiceConfig config;
    private final DB levelDb;
    private final HttpClient httpClient;
    private final ExecutorService executorService;

    public HintsClient(ServiceConfig config, DB levelDb, HttpClient httpClient, ExecutorService executorService) {
        this.config = config;
        this.levelDb = levelDb;
        this.httpClient = httpClient;
        this.executorService = executorService;
    }

    public void fetchHintsFromReplica(String replicaUrl) {
        httpClient.sendAsync(
                HttpRequest.newBuilder()
                    .GET()
                    .header(REPLICA_URL_HEADER, config.selfUrl())
                    .uri(URI.create(replicaUrl + TycoonHttpServer.HINTS_PATH))
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream()
            ).exceptionallyAsync(throwable -> {
                // TODO
                return null;
            }, executorService)
            .thenAcceptAsync(response -> {

            }, executorService);
    }
}
