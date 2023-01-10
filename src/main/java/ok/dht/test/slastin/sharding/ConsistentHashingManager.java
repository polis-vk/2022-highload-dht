package ok.dht.test.slastin.sharding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class ConsistentHashingManager implements ShardingManager {
    private final Function<String, Integer> hashFunction;
    private final List<String> clusterUrls;
    private final List<Integer> vnodes;
    private final List<Integer> nodeIndexByVnodeIndex;

    public ConsistentHashingManager(List<String> clusterUrls, int vnodesPerCluster,
                                    Function<String, Integer> hashFunction) {
        this.clusterUrls = clusterUrls;
        this.hashFunction = hashFunction;

        var vnodesMap = makeVnodesMap(clusterUrls, vnodesPerCluster, hashFunction);

        vnodes = new ArrayList<>(vnodesMap.size());
        nodeIndexByVnodeIndex = new ArrayList<>(vnodesMap.size());
        for (var entry : vnodesMap.entrySet()) {
            vnodes.add(entry.getKey());
            nodeIndexByVnodeIndex.add(entry.getValue());
        }
    }

    private static Map<Integer, Integer> makeVnodesMap(List<String> clusterUrls, int vnodesPerCluster,
                                                       Function<String, Integer> hashFunction) {
        Map<Integer, Integer> vnodesMap = new TreeMap<>();
        for (int nodeIndex = 0; nodeIndex < clusterUrls.size(); ++nodeIndex) {
            String nodeUrl = clusterUrls.get(nodeIndex);
            for (int index = 0; index < vnodesPerCluster; ++index) {
                int hash = hashFunction.apply(nodeUrl + index);
                if (vnodesMap.containsKey(hash)) {
                    throw new RuntimeException("hash collision: choose another hash function");
                }
                vnodesMap.put(hash, nodeIndex);
            }
        }
        return vnodesMap;
    }

    @Override
    public int getNodeIndexByKey(String key) {
        return nodeIndexByVnodeIndex.get(getNextVnodeIndex(key));
    }

    @Override
    public String getNodeUrlByNodeIndex(int nodeIndex) {
        return clusterUrls.get(nodeIndex);
    }

    @Override
    public List<Integer> getNodeIndices(String key, int count) {
        int vnodeIndex = getNextVnodeIndex(key);

        Set<Integer> nodeIndices = new HashSet<>(count);
        // first is same as getNodeIndexByKey
        nodeIndices.add(nodeIndexByVnodeIndex.get(vnodeIndex));

        while (nodeIndices.size() != count) {
            vnodeIndex = (vnodeIndex + 1) % vnodes.size();
            int nodeIndex = nodeIndexByVnodeIndex.get(vnodeIndex);
            nodeIndices.add(nodeIndex);
        }

        return nodeIndices.stream().toList();
    }

    private int getNextVnodeIndex(String key) {
        int hash = hashFunction.apply(key);
        return findNextHashVnodeIndex(hash);
    }

    private int findNextHashVnodeIndex(int hash) {
        final int lastIndex = vnodes.size() - 1;
        int left = -1;
        int right = lastIndex;
        while (right - left > 1) {
            int mid = (left + right) >> 1;
            if (vnodes.get(mid) < hash) {
                left = mid;
            } else {
                right = mid;
            }
        }
        return (right == lastIndex && vnodes.get(right) < hash) ? 0 : right;
    }
}
