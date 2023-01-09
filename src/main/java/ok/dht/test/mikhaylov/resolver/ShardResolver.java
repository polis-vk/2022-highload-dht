package ok.dht.test.mikhaylov.resolver;

public interface ShardResolver {
    void addShard(String shardUrl);

    void removeShard(String shardUrl);

    String resolve(String key);
}
