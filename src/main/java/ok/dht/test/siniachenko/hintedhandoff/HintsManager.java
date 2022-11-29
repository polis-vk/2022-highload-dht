package ok.dht.test.siniachenko.hintedhandoff;

import ok.dht.test.siniachenko.range.EntityChunkStreamQueueItem;

public interface HintsManager {

    void addHintForReplica(String replicaUrl, Hint hint);

    EntityChunkStreamQueueItem getReplicaHintsStream(String replicaUrl);
}
