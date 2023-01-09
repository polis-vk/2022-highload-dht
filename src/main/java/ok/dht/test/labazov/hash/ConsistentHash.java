package ok.dht.test.labazov.hash;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public final class ConsistentHash {
    private final NavigableMap<Integer, String> circle = new TreeMap<>();
    private final Hasher hasher = new Hasher();

    private int hashKey(final String key) {
        return hasher.digest(key.getBytes(StandardCharsets.UTF_8));
    }

    public String getShardByKey(final String key) {
        final Map.Entry<Integer, String> ent = circle.ceilingEntry(hashKey(key));
        if (ent == null) {
            return circle.firstEntry().getValue();
        } else {
            return ent.getValue();
        }
    }

    public void addShard(String newShard, Set<Integer> vnodeHashes) {
        for (final int vnodeHash : vnodeHashes) {
            circle.put(vnodeHash, newShard);
        }
    }

    public void removeShard(final String shard) {
        circle.values().removeIf((String x) -> x.equals(shard));
    }
}
