package ok.dht.test.shashulovskiy.sharding;

import com.google.common.base.Objects;

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
        return 11 * shardUrl.hashCode() + 727 * shardName.hashCode();
    }
}
