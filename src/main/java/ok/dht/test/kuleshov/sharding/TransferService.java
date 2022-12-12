package ok.dht.test.kuleshov.sharding;

import one.nio.util.Hash;

import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public class TransferService {
    protected boolean isTransferring;
    protected final Set<HashRange> hashRanges = new HashSet<>();
    protected final NavigableSet<Integer> circle = new TreeSet<>();

    public boolean isInTransfer(String id) {
        if (!isTransferring) {
            return false;
        }

        int hash = Hash.murmur3(id);
        Integer a = circle.ceiling(hash);
        Integer b = circle.lower(hash);

        return a != null
                && b != null
                && (hashRanges.contains(new HashRange(a, b)) || hashRanges.contains(new HashRange(b, a)));
    }

    public void clear() {
        hashRanges.clear();
        circle.clear();
        isTransferring = false;
    }
}
