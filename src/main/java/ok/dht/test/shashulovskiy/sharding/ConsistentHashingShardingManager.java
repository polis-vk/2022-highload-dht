package ok.dht.test.shashulovskiy.sharding;

import ok.dht.test.shashulovskiy.hashing.Hasher;
import one.nio.util.Utf8;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsistentHashingShardingManager implements ShardingManager {

    private final long[] virtualShards;
    private final Map<Long, Shard> hashToShard;
    private long handledKeys;
    private final String thisShardUrl;

    private final Hasher hasher;

    public ConsistentHashingShardingManager(List<String> shardUrls, String thisUrl, int vnodes, Hasher hasher) {
        this.virtualShards = new long[shardUrls.size() * vnodes];
        this.hashToShard = new HashMap<>();
        this.handledKeys = 0L;
        this.thisShardUrl = thisUrl;
        this.hasher = hasher;

        buildVirtualShards(shardUrls, vnodes);
    }

    @Override
    public Shard getShard(byte[] key) {
        var shardInd = Arrays.binarySearch(virtualShards, hasher.getHash(key));

        if (shardInd < 0) {
            shardInd = (-shardInd - 1) % virtualShards.length;
        }

        var shard = hashToShard.get(virtualShards[shardInd]);
        if (thisShardUrl.equals(shard.getShardUrl())) {
            handledKeys++;
            return null;
        } else {
            return shard;
        }
    }

    @Override
    public long getHandledKeys() {
        return handledKeys;
    }

    private void buildVirtualShards(List<String> shardUrls, int vnodes) {
        for (int shardInd = 0; shardInd < shardUrls.size(); ++shardInd) {
            for (int vnode = 0; vnode < vnodes; vnode++) {
                Shard shard = new Shard(
                        shardUrls.get(shardInd),
                        String.format("shard-%d-%d", shardInd, vnode),
                        shardInd
                );

                var shardHash = hasher.getHash(Utf8.toBytes(shard.getShardName() + shard.getShardUrl()));

                virtualShards[shardInd * vnodes + vnode] = shardHash;
                hashToShard.put(shardHash, shard);
            }
        }

        Arrays.sort(virtualShards);
    }
}
