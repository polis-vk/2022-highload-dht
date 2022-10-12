package ok.dht.test.armenakyan.sharding.model;

import ok.dht.test.armenakyan.sharding.ShardRequestHandler;

public class Shard {
    private final String url;
    private final ShardRequestHandler requestHandler;

    public Shard(String url, ShardRequestHandler requestHandler) {
        this.url = url;
        this.requestHandler = requestHandler;
    }

    public String url() {
        return url;
    }

    public ShardRequestHandler requestHandler() {
        return requestHandler;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return url.equals(((Shard) o).url);
    }
}
