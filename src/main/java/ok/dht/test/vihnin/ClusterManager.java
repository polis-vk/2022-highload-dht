package ok.dht.test.vihnin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterManager {
    private final List<String> urls;
    private final Map<String, List<String>> neighbours;

    /**
     * Constructor of ClusterManager.
     * Urls are sorted by natural order and for each url neighbours
     * are next items in urls circled (for each key it's concatenation with value
     * is cyclic shift of urls).
     *
     * Example:
     *
     * urls = ["u1", "u2", "u3"]
     * neighbours = {
     *     "u1" -> ["u2", "u3"],
     *     "u2" -> ["u3", "u1"],
     *     "u3" -> ["u1", "u2"]
     * }
     *
     * @param urls - urls
     */
    public ClusterManager(List<String> urls) {
        this.urls = new ArrayList<>(urls);
        this.urls.sort(Comparator.naturalOrder());

        this.neighbours = new HashMap<>();
        for (int i = 0; i < urls.size(); i++) {
            String currUrl = urls.get(i);
            List<String> currNeighbours = new ArrayList<>();
            for (int j = 0; j < urls.size() - 1; j++) {
                int next = (j + i + 1) % urls.size();
                currNeighbours.add(urls.get(next));
            }
            neighbours.put(currUrl, currNeighbours);
        }
    }

    public String getUrlByShard(int shard) {
        return urls.get(shard);
    }

    public int getShardByUrl(String url) {
        return urls.indexOf(url);
    }

    public int clusterSize() {
        return urls.size();
    }

    public List<String> getNeighbours(int shard) {
        return neighbours.get(getUrlByShard(shard));
    }
}
