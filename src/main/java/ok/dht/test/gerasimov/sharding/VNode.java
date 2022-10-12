package ok.dht.test.gerasimov.sharding;

/**
 * @author Michael Gerasimov
 */
public class VNode implements Comparable<VNode> {
    private final Shard shard;
    private final int hashcode;

    public VNode(Shard shard, int hashcode) {
        this.shard = shard;
        this.hashcode = hashcode;
    }

    @Override
    public int compareTo(VNode other) {
        return Integer.compare(this.hashcode, (other.hashcode));
    }

    public Shard getShard() {
        return shard;
    }

    public int getHashcode() {
        return hashcode;
    }

    @Override
    public String toString() {
        return "VNode{" +
                "shard=" + shard.getHost() + shard.getPort() +
                ", hashcode=" + hashcode +
                '}';
    }
}
