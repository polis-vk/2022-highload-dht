package ok.dht.test.armenakyan.sharding.hashing;

import ok.dht.test.armenakyan.sharding.model.Shard;
import one.nio.util.Utf8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ConsistentHashing implements Hashing {
    private static final int VIRTUAL_SHARD_COUNT = 5;
    private static final Comparator<VShard> COMPARATOR = Comparator.comparing(VShard::hash);

    private final KeyHasher keyHasher;
    private final VShard[] virtualShardsCircle;

    public ConsistentHashing(List<Shard> shards, KeyHasher keyHasher) {
        this.keyHasher = keyHasher;

        virtualShardsCircle = new VShard[shards.size() * VIRTUAL_SHARD_COUNT];

        for (int i = 0; i < shards.size(); i++) {
            Shard shard = shards.get(i);

            VShard[] virtualShards = generateVirtualShards(shard);

            System.arraycopy(
                    virtualShards,
                    0,
                    virtualShardsCircle,
                    i * VIRTUAL_SHARD_COUNT,
                    VIRTUAL_SHARD_COUNT
            );
        }

        Arrays.sort(virtualShardsCircle, COMPARATOR);
    }

    @Override
    public Shard shardByKey(String key) {
        return virtualShardByHash(keyHasher.hash(key)).shard();
    }

    private VShard virtualShardByHash(int hash) {
        int ind = Arrays.binarySearch(
                virtualShardsCircle,
                new VShard(null, hash),
                COMPARATOR
        );

        if (ind >= 0) {
            return virtualShardsCircle[ind];
        }

        int insertionPoint = -ind - 1;
        return virtualShardsCircle[insertionPoint % virtualShardsCircle.length];
    }

    private VShard[] generateVirtualShards(Shard shard) {
        VShard[] virtualShards = new VShard[VIRTUAL_SHARD_COUNT];

        ByteBuffer shardKeyBytes = ByteBuffer.wrap(Utf8.toBytes(shard.url()));

        for (byte tail = 0; tail < VIRTUAL_SHARD_COUNT; tail++) {
            shardKeyBytes.put(tail);
            int hash = keyHasher.hash(shardKeyBytes.array());

            virtualShards[tail] = new VShard(shard, hash);
        }

        return virtualShards;
    }

    private static class VShard {
        private final Shard shard;
        private final int hash;

        public VShard(Shard shard, int hash) {
            this.shard = shard;
            this.hash = hash;
        }

        public Shard shard() {
            return shard;
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

            VShard other = (VShard) o;
            return hash == other.hash && shard.equals(other.shard);
        }

        @Override
        public int hashCode() {
            return 31 * shard.hashCode() + hash;
        }
    }
}
