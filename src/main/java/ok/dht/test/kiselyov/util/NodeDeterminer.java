package ok.dht.test.kiselyov.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

import static one.nio.util.Hash.murmur3;

public class NodeDeterminer {
    private final List<ClusterNode> clusterNodes;

    public NodeDeterminer(List<String> clusterUrls) {
        this.clusterNodes = new ArrayList<>();
        for (String nodeUrl : clusterUrls) {
            clusterNodes.add(new ClusterNode(nodeUrl));
        }
    }

    public List<ClusterNode> getNodeUrls(String key, int count) {
        PriorityQueue<NodeWithHash> hashNodes = new PriorityQueue<>(new NodesHashComparator());
        for (int i = 0; i < clusterNodes.size(); i++) {
            hashNodes.add(new NodeWithHash(clusterNodes.get(i), hash(key, i)));
        }
        List<ClusterNode> maxNodes = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            maxNodes.add(Objects.requireNonNull(hashNodes.poll()).getNode());
        }
        return maxNodes;
    }

    private int hash(String key, Integer clusterNodeNum) {
        return 31 * (murmur3(key) % 101) + clusterNodeNum * 13;
    }
}
