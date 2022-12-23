package ok.dht.test.kazakov.service.sharding;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ConsistentHashingShardDeterminer<T> implements ShardDeterminer<T> {

    private static final int POINTS_PER_SHARD = 3;
    public static final int PRIME_HASH_MULTIPLIER = 1234567891;

    private final int[] hashPositions;
    private final Shard[] shards;

    public ConsistentHashingShardDeterminer(final List<Shard> shards) {
        if (shards.size() == 1) {
            this.hashPositions = new int[]{Integer.MAX_VALUE};
            this.shards = new Shard[]{shards.get(0)};
            return;
        }

        final ArrayList<Shard> sortedShards = new ArrayList<>(shards);
        sortedShards.sort(Comparator.comparing(Shard::getUrl));

        final int pointsSize = sortedShards.size() * POINTS_PER_SHARD;
        this.hashPositions = new int[pointsSize];
        this.shards = new Shard[pointsSize];

        int shardRangeSize = Integer.MAX_VALUE / pointsSize * 2;
        int shardRangeAdditions = Integer.MAX_VALUE % pointsSize * 2 + 1;
        if (shardRangeAdditions >= pointsSize) {
            shardRangeAdditions -= pointsSize;
            shardRangeSize++;
        }

        for (int i = 1; i <= pointsSize; i++) {
            final Shard shard = sortedShards.get(i % sortedShards.size());
            final int shardHashPosition = Integer.MIN_VALUE + shardRangeSize * i + Math.min(i, shardRangeAdditions);
            this.hashPositions[i - 1] = shardHashPosition;
            this.shards[i - 1] = shard;

            if (i - 2 >= 0 && shardHashPosition < this.hashPositions[i - 2]) {
                throwOnFailedInvariant("Expected hashPositions to be a sorted array.");
            }
        }

        if (this.hashPositions[this.hashPositions.length - 1] != Integer.MAX_VALUE) {
            throwOnFailedInvariant("Expected Integer.MAX_VALUE to be a point in hashPositions.");
        }
    }

    private void throwOnFailedInvariant(final String message) {
        throw new IllegalStateException(
                message
                        + "State: {hashPositions="
                        + Arrays.toString(this.hashPositions)
                        + ", shards="
                        + Arrays.toString(this.shards)
                        + "}"
        );
    }

    @Override
    public Shard determineShard(@Nonnull final T object) {
        final int hashPosition = object.hashCode() * PRIME_HASH_MULTIPLIER;
        final int pointIndex = Arrays.binarySearch(hashPositions, hashPosition);
        if (pointIndex >= 0) {
            return shards[pointIndex];
        }

        final int insertionPoint = -pointIndex - 1;
        return shards[insertionPoint];
    }
}
