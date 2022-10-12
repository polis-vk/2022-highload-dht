package ok.dht.test.shashulovskiy.sharding;

public interface ShardingManager {
    Shard getShard(byte[] key);

    long getHandledKeys();
}
