package ok.dht.test.nadutkin.impl.shards;

public abstract class Sharder {
    protected final Integer shards;

    public Sharder(Integer shards) {
        this.shards = shards;
    }

    public abstract Integer getShard(String key);
}
