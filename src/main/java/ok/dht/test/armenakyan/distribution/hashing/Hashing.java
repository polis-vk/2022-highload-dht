package ok.dht.test.armenakyan.distribution.hashing;

import ok.dht.test.armenakyan.distribution.model.Node;

public interface Hashing {
    Node nodeByKey(String key);
}
