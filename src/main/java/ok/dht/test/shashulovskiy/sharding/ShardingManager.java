package ok.dht.test.shashulovskiy.sharding;

public interface ShardingManager {
    Shard getShard(String key);
}
