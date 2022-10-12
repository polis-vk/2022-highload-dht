package ok.dht.test.ushkov;

public interface KeyManager {
    void addNode(String nodeId);

    void removeNode(String nodeId);

    String getNodeIdByKey(String key);
}
