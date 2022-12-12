package ok.dht.test.kuleshov.sharding;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kuleshov.dao.Entry;
import one.nio.serial.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class TransferSenderService extends TransferService {
    private final String selfUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Logger log = LoggerFactory.getLogger(TransferSenderService.class);

    public TransferSenderService(String selfUrl) {
        this.selfUrl = selfUrl;
    }

    public void setTransfer(Set<HashRange> hashRangeSet) {
        if (isTransferring) {
            throw new IllegalStateException("Transfer is started");
        }
        isTransferring = true;
        hashRanges.addAll(hashRangeSet);

        for (HashRange hashRange : hashRanges) {
            circle.add(hashRange.getLeftBorder());
            circle.add(hashRange.getRightBorder());
        }
    }

    public void startTransfer(Shard shard, Iterator<Entry<MemorySegment>> entryIterator) throws IOException {
        log.info("Start transfer");
        while (entryIterator.hasNext()) {
            Entry<MemorySegment> entry = entryIterator.next();

            String id = new String(entry.key().toByteArray(), StandardCharsets.UTF_8);

            if (isInTransfer(id)) {
                sendTransfer(shard, id, entry.value().toByteArray());
            }
        }

        clear();

        sendTransferEnd(shard);

        log.info("End transfer");
    }

    private void sendTransfer(Shard shard, String key, byte[] value) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    createTransferRequest(shard, key, value),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException | InterruptedException e) {
            clear();
            throw new IllegalStateException("error transferring key: " + key, e);
        }

        if (response.statusCode() != 201) {
            clear();
            throw new IllegalStateException("error transferring key: " + key + ", response: " + response.statusCode());
        }
    }

    private void sendTransferEnd(Shard shard) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    createTransferEndRequest(shard),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("error end transferring error", e);
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("error response end transferring status code: " + response.statusCode());
        }
    }

    private HttpRequest createTransferEndRequest(Shard shard) {
        String jsonShard;
        try {
            jsonShard = Json.toJson(new ShardAddBody(selfUrl, new ArrayList<>()));
        } catch (IOException e) {
            throw new IllegalStateException("error creating json from shard.", e);
        }

        return HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(jsonShard))
                .timeout(Duration.ofSeconds(2))
                .uri(URI.create(shard.getUrl() + "/v0/transend"))
                .build();
    }

    private HttpRequest createTransferRequest(Shard shard, String key, byte[] value) {
        return HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofByteArray(value))
                .timeout(Duration.ofSeconds(2))
                .uri(URI.create(shard.getUrl() + "/v0/transfer/entity?id=" + key + "&from=1&ack=1"))
                .build();
    }
}
