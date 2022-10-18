package ok.dht.test.anikina;

import one.nio.util.Hash;
import one.nio.util.Utf8;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class ConsistentHashingImpl {
    private static final int VIRTUAL_NODES_COUNT = 3;

    private final TreeMap<Long, String> hashToShard = new TreeMap<>();

    ConsistentHashingImpl(List<String> clusterUrls) {
        for (String serverUrl : clusterUrls) {
            for (int i = 0; i < VIRTUAL_NODES_COUNT; i++) {
                String virtualNode = serverUrl + "_" + i;
                hashToShard.put(hashForKey(virtualNode), serverUrl);
            }
        }
    }

    private long hashForKey(String key) {
        return key.hashCode();
    }

    String getShardByKey(String key) {
        Map.Entry<Long, String> upperBound = hashToShard.ceilingEntry(hashForKey(key));
        return upperBound == null ? hashToShard.firstEntry().getValue() : upperBound.getValue();
    }
}
