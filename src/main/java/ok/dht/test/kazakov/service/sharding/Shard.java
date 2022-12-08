package ok.dht.test.kazakov.service.sharding;

import javax.annotation.Nonnull;
import java.util.StringJoiner;

@SuppressWarnings("ClassCanBeRecord")
public final class Shard {
    private final String url;
    private final boolean isSelf;
    private final int shardIndex;

    public Shard(@Nonnull final String url,
                 final boolean isSelf,
                 final int shardIndex) {
        this.url = url;
        this.isSelf = isSelf;
        this.shardIndex = shardIndex;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Shard shard = (Shard) o;

        return shardIndex == shard.shardIndex;
    }

    @Override
    public int hashCode() {
        return shardIndex;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "Shard[", "]")
                .add("url='" + url + "'")
                .add("isSelf=" + isSelf)
                .add("shardIndex=" + shardIndex)
                .toString();
    }
}
