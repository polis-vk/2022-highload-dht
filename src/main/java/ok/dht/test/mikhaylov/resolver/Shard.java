package ok.dht.test.mikhaylov.resolver;

import java.util.Objects;

public class Shard implements Comparable<Shard> {
    private final String url;

    private final int hash;

    public Shard(String url, int hash) {
        this.url = url;
        this.hash = hash;
    }

    public String getUrl() {
        return url;
    }

    public int getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return "Shard{" +
                "url='" + url + '\'' +
                ", hash=" + hash +
                '}';
    }

    @Override
    public int compareTo(Shard o) {
        return Integer.compare(hash, o.hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shard shard = (Shard) o;
        return hash == shard.hash &&
                url.equals(shard.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, hash);
    }
}
