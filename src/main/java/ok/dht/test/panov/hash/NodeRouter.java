package ok.dht.test.panov.hash;

import ok.dht.test.panov.ReplicasAcknowledgment;
import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class NodeRouter {

    private final NavigableMap<Integer, String> hashCircle = new TreeMap<>();

    public NodeRouter(final List<String> nodeUrls) {
        int nodeNumber = nodeUrls.size();
        int key = 0;
        int step = Integer.MAX_VALUE / nodeNumber;
        for (String url : nodeUrls) {
            hashCircle.put(key, url);
            key += step;
        }
    }

    public String getUrl(final byte[] key) {
        int hash = Hash.murmur3(key, 0, key.length);
        Integer nodeUrl = hashCircle.ceilingKey(hash);
        if (nodeUrl == null) {
            return hashCircle.lastEntry().getValue();
        } else {
            return hashCircle.get(nodeUrl);
        }
    }

    public List<String> getUrls(final byte[] key, ReplicasAcknowledgment replicasAcknowledgment) {
        int hash = Hash.murmur3(key, 0, key.length);
        Integer nodeHash = hashCircle.ceilingKey(hash);
        if (nodeHash == null) {
            nodeHash = hashCircle.firstKey();
        }

        List<String> resUrls = new ArrayList<>();

        while (resUrls.size() < replicasAcknowledgment.from) {
            resUrls.add(hashCircle.get(nodeHash));
            nodeHash = hashCircle.higherKey(nodeHash);
            if (nodeHash == null) {
                nodeHash = hashCircle.firstKey();
            }
        }

        return resUrls;
    }

}
