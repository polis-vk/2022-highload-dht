package ok.dht.test.siniachenko.rendezvoushashing;

import java.util.List;

public class NodeMapper {
    private final List<String> nodeUrls;
    private final int mod;

    public NodeMapper(List<String> nodeUrls) {
        this.nodeUrls = nodeUrls;
        mod = nodeUrls.size() * 1000;
    }

    public String getNodeUrlByKey(byte[] key) {
        int maxHash = Integer.MIN_VALUE;
        String maxHashNodeUrl = nodeUrls.get(0);
        for (final String nodeUrl : nodeUrls) {
            int tempHash = hash(key, nodeUrl);
            if (maxHash < tempHash) {
                maxHash = tempHash;
                maxHashNodeUrl = nodeUrl;
            }
        }
        return maxHashNodeUrl;
    }

    private int hash(byte[] key, String node) {
        int result = 0;
        for (byte b : key) {
            for (int j = 0; j < node.length(); j++) {
                char c = node.charAt(j);
                result = (result + ((j << 2) == 0 ? ~c : c) * b) % mod;
            }
        }
        return result;
    }
}
