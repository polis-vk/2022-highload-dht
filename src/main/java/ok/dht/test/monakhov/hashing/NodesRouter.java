package ok.dht.test.monakhov.hashing;

public interface NodesRouter {
    String getNodeUrl(String key);

    String[] getNodeUrls(String key, int number);
}
