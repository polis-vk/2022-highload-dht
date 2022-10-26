package ok.dht.test.kurdyukov.sharding;

import java.util.List;

public class SimpleSharding extends Sharding {
    public SimpleSharding(List<String> urls) {
        super(urls);
    }

    @Override
    public String getShardUrlByKey(String key) {
        int index = Math.abs(key.hashCode()) % clusterUrls.size();
        return clusterUrls.get(index);
    }
}
