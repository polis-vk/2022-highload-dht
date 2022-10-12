package ok.dht.test.shashulovskiy.sharding;

public class Shard {
    private final String shardUrl;
    private final String shardName;

    public Shard(String shardUrl, String shardName) {
        this.shardUrl = shardUrl;
        this.shardName = shardName;
    }

    public String getShardUrl() {
        return shardUrl;
    }

    public String getShardName() {
        return shardName;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Use Hasher instead");
    }
}
