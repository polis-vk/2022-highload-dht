package ok.dht.test.kiselyov.util;

import java.util.Comparator;

public class NodesHashComparator implements Comparator<NodeWithHash> {
    @Override
    public int compare(NodeWithHash o1, NodeWithHash o2) {
        return o2.getHash() - o1.getHash();
    }
}
