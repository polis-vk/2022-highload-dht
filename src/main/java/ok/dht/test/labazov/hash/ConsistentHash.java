package ok.dht.test.labazov.hash;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
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

    public String getShard(final String key) {
        final Map.Entry<Integer, String> ent = circle.ceilingEntry(hashKey(key));
        if (ent == null) {
            return circle.firstEntry().getValue();
        } else {
            return ent.getValue();
        }
    }

    public List<String> getShards(final String key, final int size) {
        final Set<String> ret = new HashSet<>(size);
        ret.add(getShard(key));
        int i = 0;
        while (ret.size() < size) {
            final String candidate = getShard(key + i);
            ret.add(candidate);
            i++;
        }
        return List.copyOf(ret);
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
