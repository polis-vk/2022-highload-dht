package ok.dht.test.mikhaylov.resolver;

import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.List;

/**
 * Consistent hashing resolver.
 * Assumes constant topology.
 */
public class ConsistentHashingResolver implements ShardResolver {
    private final List<Shard> shards = new ArrayList<>();

    private interface HashFunction {
        int hash(String s);
    }

    private static final HashFunction[] HASH_FUNCTIONS = {
            Hash::murmur3,
            String::hashCode,
            s -> {
                byte[] bytes = s.getBytes();
                return Hash.xxhash(bytes, 0, bytes.length);
            }
    };

    public void addShard(String shardUrl) {
        for (HashFunction hashFunction : HASH_FUNCTIONS) {
            shards.add(new Shard(shardUrl, hashFunction.hash(shardUrl)));
        }
        shards.sort(Shard::compareTo);
    }

    public void removeShard(String shardUrl) {
        shards.removeIf(s -> s.getUrl().equals(shardUrl));
    }

    @Override
    public String resolve(String key) {
        int hash = Hash.murmur3(key);
        int i = 0;
        while (i < shards.size() && shards.get(i).getHash() <= hash) {
            i++;
        }
        return shards.get(i % shards.size()).getUrl();
    }
}
