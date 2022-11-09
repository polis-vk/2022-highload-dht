package ok.dht.test.labazov.hash;

import one.nio.util.Hash;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public final class ConsistentHash {
    private final NavigableMap<Integer, Node> circle = new TreeMap<>();

    private static int hashKey(final String key) {
        return Hash.murmur3(key);
    }

    public Node getShard(final String key) {
        final Map.Entry<Integer, Node> ent = circle.ceilingEntry(hashKey(key));
        if (ent == null) {
            return circle.firstEntry().getValue();
        } else {
            return ent.getValue();
        }
    }

    public List<Node> getShards(final String key, final int size) {
        final Set<Node> ret = new HashSet<>(size);
        ret.add(getShard(key));
        int i = 0;
        while (ret.size() < size) {
            ret.add(getShard(key + i));
            i++;
        }
        return List.copyOf(ret);
    }

    public void addShard(Node newShard, Set<Integer> vnodeHashes) {
        for (final int vnodeHash : vnodeHashes) {
            circle.put(vnodeHash, newShard);
        }
    }

    public void removeShard(final Node shard) {
        circle.values().removeIf((Node x) -> x.url().equals(shard.url()));
    }
}
