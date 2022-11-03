package ok.dht.test.kazakov.service.sharding;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

public class ConsistentHashingShardDeterminer<T> implements ShardDeterminer<T> {

    private static final int POINTS_PER_SHARD = 3;
    public static final int PRIME_HASH_MULTIPLIER = 1234567891;

    private final int totalShards;
    private final int[] hashPositions;
    private final Shard[] shards;

    public ConsistentHashingShardDeterminer(final List<Shard> sortedShards) {
        this.totalShards = sortedShards.size();

        if (sortedShards.size() == 1) {
            this.hashPositions = new int[]{Integer.MAX_VALUE};
            this.shards = new Shard[]{sortedShards.get(0)};
            return;
        }

        final int pointsSize = sortedShards.size() * POINTS_PER_SHARD;
        this.hashPositions = new int[pointsSize];
        this.shards = new Shard[pointsSize];

        int shardRangeSize = Integer.MAX_VALUE / pointsSize * 2;
        int shardRangeAdditions = Integer.MAX_VALUE % pointsSize * 2 + 1;
        if (shardRangeAdditions >= pointsSize) {
            shardRangeAdditions -= pointsSize;
            shardRangeSize++;
        }

        for (int i = 0; i < pointsSize; i++) {
            final Shard shard = sortedShards.get(i % sortedShards.size());
            final int shardHashPosition = Integer.MIN_VALUE
                    + shardRangeSize * (i + 1)
                    + Math.min(i + 1, shardRangeAdditions);
            this.hashPositions[i] = shardHashPosition;
            this.shards[i] = shard;

            if (i - 1 >= 0 && shardHashPosition < this.hashPositions[i - 1]) {
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

    @Override
    public int getTotalShards() {
        return totalShards;
    }

    @Override
    public Shard getNextShardToReplicate(@Nonnull final Shard shard) {
        // works because vnodes are has same order as shards
        return shards[(shard.getShardIndex() + 1) % totalShards];
    }
}
