package ok.dht.test.gerasimov.sharding;

import java.util.List;

public interface ConsistentHash<K> {
    Shard getShardByKey(K key);

    List<Shard> getShards();

    List<VNode> getVnodes();
}
