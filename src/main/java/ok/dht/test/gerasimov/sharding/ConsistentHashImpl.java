package ok.dht.test.gerasimov.sharding;

import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConsistentHashImpl<K> implements ConsistentHash<K> {
    private final List<VNode> vnodes;
    private final List<Shard> shards;

    public ConsistentHashImpl(List<VNode> vnodes, List<Shard> shards) {
        this.vnodes = vnodes;
        this.vnodes.sort(VNode::compareTo);
        this.shards = shards;
    }

    @Override
    public Shard getShardByKey(K key) {
        int keyHashcode = Hash.murmur3(key.toString());
        if (keyHashcode > vnodes.get(vnodes.size() - 1).getHashcode()) {
            return vnodes.get(0).getShard();
        }

        int pos = Collections.binarySearch(vnodes.stream().map(VNode::getHashcode).toList(), keyHashcode);

        if (pos >= 0) {
            return vnodes.get(pos).getShard();
        } else {
            return vnodes.get(-pos - 1).getShard();
        }
    }

    @Override
    public List<Shard> getShards() {
        return shards;
    }

    @Override
    public List<VNode> getVnodes() {
        return vnodes;
    }

    @Override
    public List<Shard> getShards(Shard start, int limit) {
        List<Shard> result = new ArrayList<>();
        result.add(start);

        for (int i = 0; i < limit - 1; i++) {
            result.add(
                    this.shards.get(
                            (result.get(result.size() - 1).getPos() + 1) % this.shards.size()
                    )
            );
        }

        return result;
    }
}
