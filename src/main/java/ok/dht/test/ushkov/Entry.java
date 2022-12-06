package ok.dht.test.ushkov;

public class Entry implements Comparable<Entry> {
    private String nodeId;
    private int hash;

    public Entry(String nodeId, int hash) {
        this.nodeId = nodeId;
        this.hash = hash;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getHash() {
        return hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    @Override
    public int compareTo(Entry other) {
        return Integer.compare(hash, other.hash);
    }
}
