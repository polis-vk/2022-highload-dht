package ok.dht.test.kurdyukov.sharding;

import one.nio.util.Hash;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConsistentHashingSharding extends Sharding {
    private final List<Point> circleHashes;
    private final int sizeVNodes;

    public ConsistentHashingSharding(
            List<String> clusterUrls,
            int countPointsForNode
    ) {
        super(clusterUrls);
        this.circleHashes = clusterUrls
                .stream()
                .flatMap(
                        url -> IntStream
                                .range(0, countPointsForNode)
                                .mapToObj(pointIndex ->
                                        new Point(
                                                Hash.murmur3(String.format("%s_node_%d", url, pointIndex)),
                                                url
                                        )
                                )
                )
                .sorted(Comparator.comparingInt(Point::hash))
                .collect(Collectors.toList());
        this.sizeVNodes = circleHashes.size();
    }

    @Override
    public String getShardUrlByKey(final String key) {
        int l = -1;
        int r = sizeVNodes;

        int hashKey = Hash.murmur3(key);

        while (r - l > 1) {
            int m = (r + l) / 2;

            if (circleHashes.get(m).hash < hashKey) {
                l = m;
            } else {
                r = m;
            }
        }

        return r == sizeVNodes ? circleHashes.get(0).url : circleHashes.get(r).url;
    }

    private record Point(int hash, String url) {
    }
}
