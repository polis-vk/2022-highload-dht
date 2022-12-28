package ok.dht.test.kiselyov.util;

public class NodeWithHash {
    private final ClusterNode node;
    private final Integer hash;

    public NodeWithHash(ClusterNode node, Integer hash) {
        this.node = node;
        this.hash = hash;
    }

    public ClusterNode getNode() {
        return node;
    }

    public Integer getHash() {
        return hash;
    }
}
