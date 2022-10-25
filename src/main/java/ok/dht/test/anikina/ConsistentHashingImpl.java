package ok.dht.test.anikina;

import one.nio.util.Hash;
import one.nio.util.Utf8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ConsistentHashingImpl {
    private static final int VIRTUAL_NODES_COUNT = 3;

    private final long[] hashes;
    private final Map<Long, String> hashToShard = new HashMap<>();

    ConsistentHashingImpl(List<String> clusterUrls) {
        hashes = new long[clusterUrls.size() * VIRTUAL_NODES_COUNT];
        for (int i = 0; i < clusterUrls.size(); i++) {
            String serverUrl = clusterUrls.get(i);
            for (int j = 0; j < VIRTUAL_NODES_COUNT; j++) {
                String virtualNode = serverUrl + "_" + j;
                long hash = hashForKey(virtualNode);
                hashes[i * VIRTUAL_NODES_COUNT + j] = hash;
                hashToShard.put(hash, serverUrl);
            }
        }
        Arrays.sort(hashes);
    }

    private long hashForKey(String key) {
        byte[] keyBytes = Utf8.toBytes(key);
        return Hash.xxhash(keyBytes, 0, keyBytes.length);
    }

    List<String> getNodesByKey(String key, int replicas) {
        long hash = hashForKey(key);
        int shardIndex = Arrays.binarySearch(hashes, hash);
        if (shardIndex < 0) {
            shardIndex = -shardIndex - 1;
        }
        if (shardIndex == 0 || shardIndex == hashes.length) {
            shardIndex = 0;
        }

        List<String> shards = new ArrayList<>();
        for (int i = 0; i < replicas; i++) {
            int index = (shardIndex + i) % hashes.length;
            shards.add(hashToShard.get(hashes[index]));
        }

        return shards;
    }
}
