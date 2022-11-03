package ok.dht.test.anikina.consistenthashing;

import one.nio.util.Hash;
import one.nio.util.Utf8;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConsistentHashingImpl {
    private static final int VIRTUAL_NODES_COUNT = 3;

    private final long[] hashes;
    private final Map<Long, String> hashToShard = new HashMap<>();

    public ConsistentHashingImpl(List<String> clusterUrls) {
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

    public Set<String> getNodesByKey(String key, int replicas) {
        long hash = hashForKey(key);

        int shardIndex = Arrays.binarySearch(hashes, hash);
        if (shardIndex < 0) {
            shardIndex = -shardIndex - 1;
        }

        Set<String> nodes = new HashSet<>();
        for (int i = 0; i < hashes.length; i++) {
            long currHash = hashes[(shardIndex + i) % hashes.length];
            nodes.add(hashToShard.get(currHash));
            if (nodes.size() == replicas) {
                break;
            }
        }
        return nodes;
    }
}
