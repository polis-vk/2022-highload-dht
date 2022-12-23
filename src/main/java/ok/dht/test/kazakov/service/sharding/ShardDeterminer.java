package ok.dht.test.kazakov.service.sharding;

import javax.annotation.Nonnull;

public interface ShardDeterminer<T> {
    Shard determineShard(@Nonnull final T object);
}
