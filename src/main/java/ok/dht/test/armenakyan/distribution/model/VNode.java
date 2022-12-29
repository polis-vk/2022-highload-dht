package ok.dht.test.armenakyan.distribution.model;

public class VNode {
    private final Node node;
    private final int hash;

    public VNode(Node node, int hash) {
        this.node = node;
        this.hash = hash;
    }

    public Node node() {
        return node;
    }

    public int hash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VNode other = (VNode) o;
        return hash == other.hash && node.equals(other.node);
    }

    @Override
    public int hashCode() {
        return 31 * node.hashCode() + hash;
    }
}
