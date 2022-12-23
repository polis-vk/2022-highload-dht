package ok.dht.test.kazakov.service.sharding;

import javax.annotation.Nonnull;
import java.util.StringJoiner;

@SuppressWarnings("ClassCanBeRecord")
public final class Shard {
    private final String url;
    private final boolean isSelf;

    public Shard(@Nonnull final String url,
                 final boolean isSelf) {
        this.url = url;
        this.isSelf = isSelf;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSelf() {
        return isSelf;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Shard shard = (Shard) o;

        if (isSelf != shard.isSelf) return false;
        return url.equals(shard.url);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (isSelf ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Shard.class.getSimpleName() + "[", "]")
                .add("url='" + url + "'")
                .add("isSelf=" + isSelf)
                .toString();
    }
}
