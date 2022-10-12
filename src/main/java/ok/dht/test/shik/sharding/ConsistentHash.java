package ok.dht.test.shik.sharding;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHash {

    private final NavigableMap<Integer, String> bounds;

    public ConsistentHash(int virtualNodesNumber, List<String> clusterUrls) {
        bounds = new TreeMap<>();
        for (String url : clusterUrls) {
            for (int i = 0; i < virtualNodesNumber; ++i) {
                int hash = Arrays.hashCode((url + i + url).getBytes(StandardCharsets.UTF_8));
                bounds.put(hash, url);
            }
        }
    }

    public String getShardUrlByKey(byte[] key) {
        int hash = Arrays.hashCode(key);
        Map.Entry<Integer, String> floorEntry = bounds.floorEntry(hash);
        if (floorEntry == null) {
            return bounds.lastEntry().getValue();
        }
        return floorEntry.getValue();
    }

}

