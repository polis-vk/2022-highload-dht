package ok.dht.test.kuleshov.sharding;

import java.util.Objects;

public class CircleRange implements Comparable<CircleRange> {
    private final Shard shard;
    private final HashRange hashRange;

    public CircleRange(Shard shard, HashRange hashRange) {
        this.shard = shard;
        this.hashRange = hashRange;
    }

    public Shard getShard() {
        return shard;
    }

    public HashRange getHashRange() {
        return hashRange;
    }

    @Override
    public int compareTo(CircleRange other) {
        return Integer.compare(hashRange.getRightBorder(), other.getHashRange().getRightBorder());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CircleRange that = (CircleRange) o;
        return Objects.equals(shard, that.shard) && Objects.equals(hashRange, that.hashRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shard, hashRange);
    }

    @Override
    public String toString() {
        return "{" + hashRange.getLeftBorder() + " " + shard.getUrl() + " " + hashRange.getRightBorder() + '}';
    }
}
