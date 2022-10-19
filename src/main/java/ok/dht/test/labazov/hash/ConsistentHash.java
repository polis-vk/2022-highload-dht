package ok.dht.test.labazov.hash;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ConsistentHash {
    private final TreeMap<Integer, String> circle = new TreeMap<>();
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

    private void addRangeGlueing(Map<String, HashSet<HashRange>> ans,
                                 Map.Entry<Integer, String> ent,
                                 IntStringPair prevVnodeHash) {
        final HashSet<HashRange> sett = ans.computeIfAbsent(ent.getValue(), (String x) -> new HashSet<>());
        if (ent.getValue().equals(prevVnodeHash.second)) {
            for (final HashRange hr : sett) {
                if (hr.rightBorder == prevVnodeHash.first) {
                    sett.remove(hr);
                    sett.add(new HashRange(hr.leftBorder, ent.getKey()));
                    break;
                }
            }
        } else {
            sett.add(new HashRange(prevVnodeHash.first + 1, ent.getKey()));
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

    private record IntStringPair(int first, String second) {
    }

    public record HashRange(int leftBorder, int rightBorder) {
    }
}
