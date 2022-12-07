package ok.dht.test.kuleshov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kuleshov.dao.Entry;
import ok.dht.test.kuleshov.dao.storage.MemorySegmentComparator;
import ok.dht.test.kuleshov.sharding.HashRange;
import ok.dht.test.kuleshov.sharding.Shard;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public class TransferService {
    private volatile String currentTransId;
    private volatile MemorySegment currentSegment;
    private boolean isTransferStarted;
    private Set<HashRange> hashRanges;
    private final NavigableSet<Integer> circle = new TreeSet<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Logger log = LoggerFactory.getLogger(TransferService.class);

    public boolean isTransfer(String id) {
        return isTransferStarted && currentTransId.equals(id);
    }

    public boolean isTransferred(MemorySegment id) {
        return MemorySegmentComparator.INSTANCE.compare(currentSegment, id) > 0;
    }

    public Status getSegmentStatus(MemorySegment id) {
        if (isInTransfer(new String(id.toByteArray(), StandardCharsets.UTF_8))) {
            return Status.TRANSFERRING;
        }

        return Status.LOCAL;
    }

    public void transfer(Shard shard, Set<HashRange> hashRangeSet, Iterator<Entry<MemorySegment>> entryIterator) {
        log.info("Start transfer");
        if (isTransferStarted) {
            throw new IllegalStateException("Transfer is started");
        }
        isTransferStarted = true;
        hashRanges = hashRangeSet;

        for (HashRange hashRange : hashRanges) {
            circle.add(hashRange.getLeftBorder());
            circle.add(hashRange.getRightBorder());
        }

        while (entryIterator.hasNext()) {
            Entry<MemorySegment> entry = entryIterator.next();

            String id = new String(entry.key().toByteArray(), StandardCharsets.UTF_8);

            if (!isInTransfer(id)) {
                continue;
            }

            currentTransId = id;
            currentSegment = entry.key();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(entry.value().toByteArray()))
                    .timeout(Duration.ofSeconds(2))
                    .uri(URI.create(shard.getUrl() + "?id=" + id + "&from=1&ack=1"))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException | InterruptedException e) {
                circle.clear();
                hashRangeSet.clear();
                isTransferStarted = false;
                currentTransId = null;
                currentSegment = null;
                throw new IllegalStateException("error transerring key: " + id + ", error: " + e.getMessage());
            }

            if (response.statusCode() != 201) {
                circle.clear();
                hashRangeSet.clear();
                isTransferStarted = false;
                currentTransId = null;
                currentSegment = null;
                throw new IllegalStateException("error transerring key: " + id + ", response: " + response.statusCode());
            }
        }

        circle.clear();
        hashRangeSet.clear();
        isTransferStarted = false;
        currentTransId = null;
        currentSegment = null;

        log.info("End transfer");
    }

    public boolean isInTransfer(String id) {
        int hash = Hash.murmur3(id);
        Integer a = circle.ceiling(hash);
        Integer b = circle.floor(hash);

        return a != null && b != null && (hashRanges.contains(new HashRange(a, b)) || hashRanges.contains(new HashRange(b, a)));
    }

    public enum Status {
        NEW_SHARD, LOCAL, TRANSFERRING
    }
}
