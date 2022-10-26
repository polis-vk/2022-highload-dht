package ok.dht.test.siniachenko.rendezvoushashing;

import java.util.Arrays;
import java.util.List;

public class NodeMapper {
    private final List<String> nodeUrls;
    private final int mod;

    public NodeMapper(List<String> nodeUrls) {
        this.nodeUrls = nodeUrls;
        mod = nodeUrls.size() * 1000;
    }

    public List<String> getNodeUrls() {
        return nodeUrls;
    }

    // TODO: think very hard about replacing rendezvous hash with consistent hash
    // because of memory and concurrency improvements
    public long[] getNodeUrlsByKey(byte[] key) {
        long[] hashesAndNodes = new long[nodeUrls.size()];
        for (int i = 0; i < nodeUrls.size(); i++) {
            String nodeUrl = nodeUrls.get(i);
            // TODO: fix koctil with nodes no in hashes
            hashesAndNodes[i] = i + (((long) hash(key, nodeUrl)) << 32);
        }
        Arrays.sort(hashesAndNodes);
        return hashesAndNodes;
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
