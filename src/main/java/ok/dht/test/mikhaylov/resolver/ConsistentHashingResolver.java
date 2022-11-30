package ok.dht.test.mikhaylov.resolver;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import one.nio.util.Hash;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Consistent hashing resolver.
 * Assumes constant topology.
 */
public class ConsistentHashingResolver implements ShardResolver {
    private final List<Shard> shards = new ArrayList<>();

    private static final HashFunction[] HASH_FUNCTIONS = {
            Hashing.murmur3_128(0x486fdec0),
            Hashing.murmur3_128(0x148a976e),
            Hashing.murmur3_128(0x6dbcadbb),
            Hashing.murmur3_128(0x297efa28),
            Hashing.murmur3_128(0x008142c8)
    };

    public void addShard(String shardUrl) {
        for (HashFunction hashFunction : HASH_FUNCTIONS) {
            shards.add(new Shard(shardUrl, hashFunction.hashString(shardUrl, StandardCharsets.UTF_8).asInt()));
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
