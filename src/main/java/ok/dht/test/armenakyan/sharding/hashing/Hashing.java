package ok.dht.test.armenakyan.sharding.hashing;

import ok.dht.test.armenakyan.sharding.model.Shard;

public interface Hashing {
    Shard shardByKey(String key);
}
