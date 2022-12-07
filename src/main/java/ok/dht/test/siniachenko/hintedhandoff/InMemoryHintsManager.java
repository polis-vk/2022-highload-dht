package ok.dht.test.siniachenko.hintedhandoff;

import ok.dht.test.siniachenko.range.ChunkedTransferEncoder;
import ok.dht.test.siniachenko.range.EntityChunkStreamQueueItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class InMemoryHintsManager implements HintsManager {
    private final ChunkedTransferEncoder chunkedTransferEncoder;
    private final Map<String, Map<byte[], byte[]>> replicaUrlToHints;

    public InMemoryHintsManager(ChunkedTransferEncoder chunkedTransferEncoder) {
        this.chunkedTransferEncoder = chunkedTransferEncoder;
        replicaUrlToHints = new HashMap<>();
    }

    @Override
    public void addHintForReplica(String replicaUrl, Hint hint) {
        Map<byte[], byte[]> hints = replicaUrlToHints.computeIfAbsent(replicaUrl, r -> new HashMap<>());
        hints.put(hint.key, hint.value);
    }

    @Override
    public EntityChunkStreamQueueItem getReplicaHintsStream(String replicaUrl) {
        Iterator<Map.Entry<byte[], byte[]>> replicaHintsIterator;
        Map<byte[], byte[]> hints = replicaUrlToHints.get(replicaUrl);
        if (hints == null) {
            replicaHintsIterator = Collections.emptyIterator();
        } else {
            replicaHintsIterator = hints.entrySet().iterator();
        }
        return chunkedTransferEncoder.encodeEntityChunkStream(
            replicaHintsIterator, true, true
        );
    }

    @Override
    public void deleteHintsForReplica(String replicaUrl) {
        replicaUrlToHints.remove(replicaUrl);
    }
}
