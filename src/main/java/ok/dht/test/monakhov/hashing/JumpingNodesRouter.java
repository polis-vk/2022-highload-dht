package ok.dht.test.monakhov.hashing;

import one.nio.util.Hash;

import java.util.List;
import java.util.Random;

public class JumpingNodesRouter implements NodesRouter {
    private final List<String> urls;

    public JumpingNodesRouter(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public String getNodeUrl(String key) {
        return urls.get(getNodeIndex(Hash.murmur3(key)));
    }

    private int getNodeIndex(int key) {
        var random = new Random();
        random.setSeed(key);
        int b = -1;
        int j = 0;
        while (j < urls.size()) {
            b = j;
            double r = random.nextDouble();
            j = (int) Math.floor((b + 1) / r);
        }
        return b;
    }
}
