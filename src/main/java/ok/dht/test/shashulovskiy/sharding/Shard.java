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
        throw new UnsupportedOperationException("Use Hasher instead");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shard shard = (Shard) o;
        return Objects.equal(shardUrl, shard.shardUrl) && Objects.equal(shardName, shard.shardName);
    }
}
