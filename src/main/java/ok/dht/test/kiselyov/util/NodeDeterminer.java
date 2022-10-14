package ok.dht.test.kiselyov.util;

import java.util.List;

import static one.nio.util.Hash.murmur3;

public class NodeDeterminer {
    private final List<ClusterNode> clusterNodes;

    public NodeDeterminer(List<ClusterNode> clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public ClusterNode getNodeUrl(String key) {
        int max = 0;
        int maxNode = 0;
        for (int i = 0; i < clusterNodes.size(); i++) {
            int hashSum = hash(key, i);
            if (hashSum > max) {
                max = hashSum;
                maxNode = i;
            }
        }
        return clusterNodes.get(maxNode);
    }

    private int hash(String key, Integer clusterNodeNum) {
        return 31 * (murmur3(key) % 101) + clusterNodeNum * 13;
    }
}
