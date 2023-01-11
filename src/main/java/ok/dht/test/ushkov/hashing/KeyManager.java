package ok.dht.test.ushkov.hashing;

import java.util.List;

public interface KeyManager {
    void addNode(String nodeId);

    void removeNode(String nodeId);

    List<String> getNodeIdsByKey(String key, int n);
}
