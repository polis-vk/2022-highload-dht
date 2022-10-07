package ok.dht.test.siniachenko.rendezvoushashing;

import java.util.Arrays;
import java.util.List;

public class NodeMapper {
    private final List<String> nodeUrls;

    public NodeMapper(List<String> nodeUrls) {
        this.nodeUrls = nodeUrls;
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

    private static int hash(byte[] key, String node) {
        // TODO: THIS HASH IS MONOTONIC BY NODE NO
        return Arrays.hashCode(key) * 1009 + node.hashCode();
    }
}
