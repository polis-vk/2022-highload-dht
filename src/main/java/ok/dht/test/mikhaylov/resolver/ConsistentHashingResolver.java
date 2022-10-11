package ok.dht.test.mikhaylov.resolver;

import java.util.List;

/**
 * Consistent hashing resolver.
 * Assumes constant topology.
 */
public class ConsistentHashingResolver implements ShardResolver {
    private final List<String> shards;

    public ConsistentHashingResolver(List<String> shards) {
        this.shards = shards;
    }

    @Override
    public String resolve(String key) {
        int hash = key.hashCode();
        int shardIndex = Math.abs(hash % shards.size());
        return shards.get(shardIndex);
    }
}
