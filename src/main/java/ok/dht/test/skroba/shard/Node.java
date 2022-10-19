package ok.dht.test.skroba.shard;

public class Node {
    private final String url;
    private final int hash;
    
    Node(String url, int id) {
        this.url = url;
        this.hash = id;
    }
    
    public String getUrl() {
        return url;
    }
    
    public int getHash() {
        return hash;
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
}
