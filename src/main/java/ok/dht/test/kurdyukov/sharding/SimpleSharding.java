package ok.dht.test.kurdyukov.sharding;

import one.nio.util.Hash;

import java.util.List;

public class SimpleSharding implements Sharding {
    private final List<String> urls;

    public SimpleSharding(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public String getShardUrlByKey(String key) {
        int index = Math.abs(Hash.murmur3(key)) % urls.size();
        return urls.get(index);
    }
}
