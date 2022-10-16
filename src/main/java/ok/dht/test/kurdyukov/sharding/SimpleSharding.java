package ok.dht.test.kurdyukov.sharding;

import java.util.List;

public class SimpleSharding implements Sharding {
    private final List<String> urls;

    public SimpleSharding(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public String getShardUrlByKey(String key) {
        int index = Math.abs(key.hashCode()) % urls.size();
        return urls.get(index);
    }
}
