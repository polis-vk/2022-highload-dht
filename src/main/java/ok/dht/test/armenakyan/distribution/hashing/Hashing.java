package ok.dht.test.armenakyan.distribution.hashing;

import ok.dht.test.armenakyan.distribution.model.Node;

import java.util.Set;

public interface Hashing {
    Node nodeByKey(String key);

    Set<Node> nodesByKey(String key, int nodeCount);
}
