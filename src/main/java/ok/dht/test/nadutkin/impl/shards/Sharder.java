package ok.dht.test.nadutkin.impl.shards;

import java.util.List;

public abstract class Sharder {
    protected final List<String> shards;

    public Sharder(List<String> shards) {
        this.shards = shards;
    }

    public abstract List<String> getShardUrls(String key, int from);
}
