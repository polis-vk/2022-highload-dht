package ok.dht.test.kuleshov.sharding;

import ok.dht.test.kuleshov.utils.CoolPair;
import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class ConsistentHashingManager {
    private static final int DEFAULT_VNODES = 4;

    private final Map<Shard, Set<HashRange>> shardToHashRanges = new ConcurrentHashMap<>();
    private final NavigableSet<CircleRange> circle = new TreeSet<>();

    public Shard getShardByKey(String key) {
        return getCircleNext(circle, Hash.murmur3(key)).getShard();
    }

    public ConsistentHashingManager(List<String> clusters) {
        for (String cluster : clusters) {
            addNode(cluster);
        }
    }

    public ConsistentHashingManager() {
    }

    public Map<Shard, Set<HashRange>> addShard(Shard newShard, List<Integer> vnodeHashes) {
        if (circle.isEmpty()) {
            int first = vnodeHashes.get(vnodeHashes.size() - 1);
            HashRange range = new HashRange(first + 1, first);

            circle.add(new CircleRange(newShard, range));
            shardToHashRanges.put(newShard, new HashSet<>(List.of(range)));

            List<Integer> tail = new ArrayList<>(List.copyOf(vnodeHashes));
            tail.remove(vnodeHashes.size() - 1);

            addShard(newShard, tail);

            return Map.of();
        }

        ConcurrentHashMap<Shard, Set<HashRange>> result = new ConcurrentHashMap<>();
        List<Integer> vnodeHashSorted = new ArrayList<>();
        vnodeHashSorted.addAll(vnodeHashes.stream().filter(it -> circle.last().getHashRange().getRightBorder() < it).sorted().toList());
        vnodeHashSorted.addAll(vnodeHashes.stream().filter(it -> circle.last().getHashRange().getRightBorder() > it).sorted().toList());

        for (int i = vnodeHashSorted.size() - 1; i >= 0; i--) {
            int it = vnodeHashSorted.get(i);
            CoolPair<Shard, HashRange> pair = addShard(newShard, it);
            Shard shard = pair.getFirst();
            HashRange hashRange = pair.getSecond();

            if (shard != newShard) {
                result.putIfAbsent(shard, new HashSet<>());
                result.get(shard).add(hashRange);
            }
        }

        System.out.println(new ArrayList<>(circle));
        return result;
    }

    private CoolPair<Shard, HashRange> addShard(Shard newShard, int vnodeHash) {
        CircleRange circleRange = getCircleNext(circle, vnodeHash);
        CoolPair<HashRange, HashRange> pair = circleRange.getHashRange().split(vnodeHash);

        HashRange newShardRange = pair.getFirst();
        HashRange oldRange = pair.getSecond();

        circle.remove(circleRange);
        circle.add(new CircleRange(newShard, newShardRange));
        circle.add(new CircleRange(circleRange.getShard(), oldRange));

        shardToHashRanges.putIfAbsent(newShard, new HashSet<>());
        shardToHashRanges.get(newShard).add(newShardRange);
        shardToHashRanges.get(circleRange.getShard()).remove(circleRange.getHashRange());
        shardToHashRanges.get(circleRange.getShard()).add(oldRange);

        return new CoolPair<>(circleRange.getShard(), newShardRange);
    }

    private CircleRange getCircleNext(NavigableSet<CircleRange> circle, int e) {
        CircleRange next = circle.ceiling(new CircleRange(new Shard(""), new HashRange(0, e)));
        return next != null ? next : circle.first();
    }

    public Map<Shard,Set<HashRange>> addNode(String url) {
        return addNode(url, DEFAULT_VNODES);
    }

    public Map<Shard,Set<HashRange>> addNode(String url, int vnodes) {
        List<Integer> vnodeHashes = new ArrayList<>();
        for (int i = 1; i <= vnodes; i++) {
            vnodeHashes.add(Hash.murmur3(url + "|" + i));
        }

        return addShard(new Shard(url), vnodeHashes);
    }
}
