package ok.dht.test.slastin.sharding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class ConsistentHashingManager implements ShardingManager {
    private final Function<String, Integer> hashFunction;
    private final List<Integer> vnodeHashes;
    private final List<String> vnodeUrls;

    public ConsistentHashingManager(List<String> clusterUrls, int vnodesPerCluster,
                                    Function<String, Integer> hashFunction) {
        this.hashFunction = hashFunction;

        var vnodesMap = makeVnodesMap(clusterUrls, vnodesPerCluster, hashFunction);

        vnodeHashes = new ArrayList<>(vnodesMap.size());
        vnodeUrls = new ArrayList<>(vnodesMap.size());

        for (var entry : vnodesMap.entrySet()) {
            vnodeHashes.add(entry.getKey());
            vnodeUrls.add(entry.getValue());
        }
    }

    private static Map<Integer, String> makeVnodesMap(List<String> clusterUrls, int vnodesPerCluster,
                                                      Function<String, Integer> hashFunction) {
        Map<Integer, String> vnodesMap = new TreeMap<>();
        for (String url : clusterUrls) {
            for (int index = 0; index < vnodesPerCluster; ++index) {
                int hash = hashFunction.apply(url + index);
                if (vnodesMap.containsKey(hash)) {
                    throw new RuntimeException("hash collision: choose another hash function");
                }
                vnodesMap.put(hash, url);
            }
        }
        return vnodesMap;
    }

    @Override
    public String getNodeUrlByKey(String key) {
        int hash = hashFunction.apply(key);
        int index = findNextHashIndex(hash);
        return vnodeUrls.get(index);
    }

    private int findNextHashIndex(int hash) {
        int lastIndex = vnodeHashes.size() - 1;
        int left = -1;
        int right = lastIndex;
        while (right - left > 1) {
            int mid = (left + right) >> 1;
            if (vnodeHashes.get(mid) < hash) {
                left = mid;
            } else {
                right = mid;
            }
        }
        return (right == lastIndex && vnodeHashes.get(right) < hash) ? 0 : right;
    }
}
