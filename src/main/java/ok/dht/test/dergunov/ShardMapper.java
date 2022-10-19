package ok.dht.test.dergunov;

import one.nio.util.Hash;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShardMapper {
    private final long[] rangeHashes;

    private final Map<Long, String> mappingHashShard = new HashMap<>();

    public ShardMapper(List<String> clusterUrls) {
        rangeHashes = new long[clusterUrls.size()];
        String node;
        long hash;
        for (int i = 0; i < clusterUrls.size(); i++) {
            node = clusterUrls.get(i);
            hash = fromKeyToHash(node);
            rangeHashes[i] = hash;
            mappingHashShard.put(hash, node);
        }
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

