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
        return getHashRange(id) != null;
    }

    protected HashRange getHashRange(String id) {
        if (!isTransferring) {
            return null;
        }

        int hash = Hash.murmur3(id);
        Integer a = circle.ceiling(hash);
        if (a == null) {
            a = circle.ceiling(Integer.MIN_VALUE);
        }
        Integer b = circle.lower(hash);
        if (b == null) {
            b = circle.lower(Integer.MAX_VALUE);
        }
        if (a == null || b == null) {
            return null;
        }

        HashRange range1 = new HashRange(a, b);
        if (hashRanges.contains(range1)) {
            return range1;
        }

        HashRange range2 = new HashRange(b, a);
        if (hashRanges.contains(range2)) {
            return range2;
        }

        return null;
    }

    public void clear() {
        hashRanges.clear();
        circle.clear();
        isTransferring = false;
    }
}
