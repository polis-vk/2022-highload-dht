package ok.dht.test.shashulovskiy.sharding;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ConsistentHashingShardingManager implements ShardingManager {

    private final ConcurrentNavigableMap<Integer, Shard> virtualShards;
    private final String thisShardUrl;

    public ConsistentHashingShardingManager(List<String> shardUrls, String thisUrl, int vnodes) {
        this.virtualShards = buildVirtualShards(shardUrls, thisUrl, vnodes);
        this.thisShardUrl = thisUrl;
    }

    public Shard getShard(String key) {
        // TODO GOOD HASH
        var shard1 = virtualShards.ceilingEntry(key.hashCode());
        var shard = shard1.getValue();

        if (shard == null) {
            shard = virtualShards.firstEntry().getValue();
        }

        if (thisShardUrl.equals(shard.getShardUrl())) {
            return null;
        } else {
            return shard;
        }
    }

    private ConcurrentNavigableMap<Integer, Shard> buildVirtualShards(List<String> shardUrls, String thisUrl, int vnodes) {
        ConcurrentNavigableMap<Integer, Shard> result = new ConcurrentSkipListMap<>();

        for (int shardInd = 0; shardInd < shardUrls.size(); ++shardInd) {
            for (int vnode = 0; vnode < vnodes; vnode++) {
                Shard shard = new Shard(
                        shardUrls.get(shardInd),
                        String.format("shard-%d-%d", shardInd, vnode)
                );

                result.put(shard.hashCode(), shard);
            }
        }

        return result;
    }
}
