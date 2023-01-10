package ok.dht.test.kurdyukov.sharding;

import java.util.ArrayList;
import java.util.List;

public abstract class Sharding {
    protected final List<String> clusterUrls;

    protected Sharding(List<String> clusterUrls) {
        this.clusterUrls = clusterUrls;
    }

    protected abstract String getShardUrlByKey(String key);

    public List<String> getClusterUrlsByCount(final String key, int formParam) {
        final ArrayList<String> nodes = new ArrayList<>();
        final String firstNode = getShardUrlByKey(key);

        nodes.add(firstNode);
        int startIndex = clusterUrls.indexOf(firstNode);
        int form = formParam;

        while (--form > 0) {
            nodes.add(clusterUrls.get(++startIndex % clusterUrls.size()));
        }

        return nodes;
    }
}
