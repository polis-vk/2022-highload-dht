package ok.dht.test.mikhaylov.resolver;

import java.util.List;

/**
 * Consistent hashing resolver.
 * Assumes constant topology.
 */
public class ConsistentHashingResolver implements ShardResolver {
    private final List<String> shards;

    private static final int VNODE_COUNT = 100;

    public ConsistentHashingResolver(List<String> shards) {
        this.shards = shards.stream()
                .sorted()
                .toList();
    }

    @Override
    public String resolve(String key) {
        int hash = key.hashCode();
        int shardIndex = Math.abs(hash % (shards.size() * VNODE_COUNT));
        return shards.get(shardIndex / VNODE_COUNT);
    }
}
