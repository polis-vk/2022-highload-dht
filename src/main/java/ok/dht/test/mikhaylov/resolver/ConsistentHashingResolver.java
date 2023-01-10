package ok.dht.test.mikhaylov.resolver;

/**
 * Consistent hashing resolver.
 * Assumes constant topology.
 */
public class ConsistentHashingResolver implements ShardResolver {
    private final int shardCount;

    private static final int VNODE_COUNT = 100;

    public ConsistentHashingResolver(int shardCount) {
        this.shardCount = shardCount;
    }

    @Override
    public int resolve(String key) {
        int hash = key.hashCode();
        int shardIndex = Math.abs(hash % (shardCount * VNODE_COUNT));
        return shardIndex / VNODE_COUNT;
    }
}
