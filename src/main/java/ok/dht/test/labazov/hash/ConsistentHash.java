package ok.dht.test.labazov.hash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ConsistentHash {
    private final NavigableMap<Integer, String> circle = new TreeMap<>();
    private final Hasher hasher = new Hasher();

    private int hashKey(final String key) {
        return hasher.digest(key.getBytes(StandardCharsets.UTF_8));
    }

    public String getShardByKey(final String key) {
        final Map.Entry<Integer, String> ent = circle.ceilingEntry(hashKey(key));
        if (ent != null) {
            return ent.getValue();
        } else {
            return circle.firstEntry().getValue();
        }
    }

    private void addRangeGlueing(HashMap<String, HashSet<HashRange>> ans, Map.Entry<Integer, String> ent, IntStringPair prevVnodeHash) {
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

    private HashMap<String, HashSet<HashRange>> generateMap() {
        final HashMap<String, HashSet<HashRange>> ans = new HashMap<>();
        IntStringPair prevVnodeHash = null;

        for (final Map.Entry<Integer, String> ent : circle.entrySet()) {
            if (prevVnodeHash != null) {
                addRangeGlueing(ans, ent, prevVnodeHash);
            } else {
                ans.put(ent.getValue(), new HashSet<>(List.of(new HashRange(Integer.MIN_VALUE, ent.getKey()))));
            }

            prevVnodeHash = new IntStringPair(ent.getKey(), ent.getValue());
        }
        if (prevVnodeHash != null) {
            final String entVal = circle.firstEntry().getValue();

            final HashSet<HashRange> sett = ans.get(entVal);
            if (entVal.equals(prevVnodeHash.second)) {

                for (final HashRange hr : sett) {
                    if (hr.rightBorder == prevVnodeHash.first) {
                        sett.remove(hr);
                        sett.add(new HashRange(hr.leftBorder, Integer.MAX_VALUE));
                        break;
                    }
                }
            } else {
                sett.add(new HashRange(prevVnodeHash.first + 1, Integer.MAX_VALUE));
            }
            outer:
            for (final HashRange range1 : sett) {
                for (final HashRange range2 : sett) {
                    if (range1 == range2) {
                        continue;
                    }
                    if (range1.rightBorder == Integer.MAX_VALUE && range2.leftBorder == Integer.MIN_VALUE) {
                        sett.remove(range1);
                        sett.remove(range2);
                        sett.add(new HashRange(range1.leftBorder, range2.rightBorder));
                        break outer;
                    } else if (range2.rightBorder == Integer.MAX_VALUE && range1.leftBorder == Integer.MIN_VALUE) {
                        sett.remove(range1);
                        sett.remove(range2);
                        sett.add(new HashRange(range2.leftBorder, range1.rightBorder));
                        break outer;
                    }
                }
            }
        }
        return ans;
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
}
