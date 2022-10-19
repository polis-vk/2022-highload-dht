package ok.dht.test.dergunov;

import one.nio.util.Hash;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShardMapper {
    private static final int VIRTUAL_NODES = 3;
    private static final char DELIMITER = ':';
    private final long[] rangeHashes;

    private final Map<Long, String> mappingHashShard = new HashMap<>();

    public ShardMapper(List<String> clusterUrls) {
        rangeHashes = new long[clusterUrls.size() * VIRTUAL_NODES];
        String node;
        String virtualNode;
        long hash;
        for (int i = 0; i < clusterUrls.size(); i++) {
            node = clusterUrls.get(i);
            for (int j = 0; j < VIRTUAL_NODES; j++) {
                virtualNode = node + DELIMITER + j;
                hash = fromKeyToHash(virtualNode);
                rangeHashes[i * VIRTUAL_NODES + j] = hash;
                mappingHashShard.put(hash, node);
            }
        }
        Arrays.sort(rangeHashes);
    }

    String getShardByKey(String key) {
        long hash = fromKeyToHash(key);
        int indexHash = searchIndex(rangeHashes, hash);
        return mappingHashShard.get(rangeHashes[indexHash]);
    }

    private static long fromKeyToHash(String key) {
        return Hash.murmur3(key);
    }

    private static int searchIndex(long[] arr, long hash) {
        int left = 0;
        int right = arr.length;
        while (left < right - 1) {
            int mid = (left + right) >>> 1;
            if (arr[mid] <= hash) {
                left = mid;
            } else {
                right = mid;
            }
        }
        return left;
    }
}

