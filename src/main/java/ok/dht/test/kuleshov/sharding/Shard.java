package ok.dht.test.kuleshov.sharding;

import java.util.Objects;

public class Shard {
    private final String url;

    public Shard(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shard shard = (Shard) o;
        return Objects.equals(url, shard.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "Shard{"
                + "url='" + url + '\''
                + '}';
    }
}
