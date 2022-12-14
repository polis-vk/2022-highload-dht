package ok.dht.test.kuleshov.sharding;

import ok.dht.test.kuleshov.utils.CoolPair;
import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class ConsistentHashService {
    private final Map<Shard, Set<HashRange>> shardToHashRanges = new ConcurrentHashMap<>();
    private final NavigableSet<CircleRange> circle = new TreeSet<>();
    private final List<Shard> cluster = new ArrayList<>();
    private final Map<Shard, Integer> shardIndexMap = new HashMap<>();

    public Shard getShardByKey(String key) {
        return getCircleNext(circle, Hash.murmur3(key)).getShard();
    }

    public int clusterSize() {
        return cluster.size();
    }

    public List<Shard> getShardsByKey(String key, int vnodes) {
        final int startIndex = shardIndexMap.get(getShardByKey(key));

        List<Shard> shards = new ArrayList<>();
        for (int reqIndex = 0; reqIndex < vnodes; reqIndex++) {
            shards.add(cluster.get((startIndex + reqIndex) % cluster.size()));
        }

        return shards;
    }

    public Map<Shard, Set<HashRange>> addShard(ShardAddBody shardAddBody, int vnodes) {
        if (shardAddBody.getHashes() == null || shardAddBody.getHashes().isEmpty()) {
            return addShard(new Shard(shardAddBody.getUrl()), vnodes);
        }

        return addShard(new Shard(shardAddBody.getUrl()), shardAddBody.getHashes());
    }

    public Map<Shard, Set<HashRange>> addShard(Shard shard, int vnodes) {
        List<Integer> vnodeHashes = new ArrayList<>();
        for (int i = 1; i <= vnodes; i++) {
            vnodeHashes.add(Hash.murmur3(shard.getUrl() + "|" + i));
        }

        return addShard(shard, vnodeHashes);
    }

    public Map<Shard, Set<HashRange>> addShard(Shard newShard, List<Integer> vnodeHashes) {
        cluster.add(newShard);
        cluster.sort(Comparator.comparing(Shard::getUrl));
        updateIndexMap();

        if (circle.isEmpty()) {
            addShardEmpty(newShard, vnodeHashes);

            return Map.of();
        }

        ConcurrentHashMap<Shard, Set<HashRange>> result = new ConcurrentHashMap<>();
        List<Integer> vnodeHashSorted = splitAndSort(circle.last().getHashRange().getRightBorder(), vnodeHashes);

        for (int i = vnodeHashSorted.size() - 1; i >= 0; i--) {
            int it = vnodeHashSorted.get(i);
            CoolPair<Shard, HashRange> pair = addShardByHash(newShard, it);
            Shard shard = pair.getFirst();
            HashRange hashRange = pair.getSecond();

            if (!shard.equals(newShard)) {
                result.putIfAbsent(shard, new HashSet<>());
                result.get(shard).add(hashRange);
            }
        }

        return result;
    }

    public Map<Shard, Set<HashRange>> removeShard(Shard shard) {
        if (circle.stream().allMatch(a -> a.getShard().equals(shard))) {
            circle.clear();
            shardToHashRanges.clear();

            return Map.of();
        }

        Map<Shard, Set<HashRange>> result = new HashMap<>();

        CircleRange last = getLastNotSame(shard);

        if (last == null) {
            return Map.of();
        }

        List<Integer> vnodeHashSorted = splitAndSort(
                last.getHashRange().getRightBorder(),
                shardToHashRanges.get(shard).stream().map(HashRange::getRightBorder).toList()
        );

        for (Integer it : vnodeHashSorted) {
            if (circle.size() == 1) {
                circle.clear();
                shardToHashRanges.clear();

                return result;
            }
            CoolPair<Shard, HashRange> pair = removeShard(shard, it);

            Shard shardNext = pair.getFirst();
            HashRange hashRange = pair.getSecond();
            if (!shardNext.equals(shard)) {
                result.putIfAbsent(shardNext, new HashSet<>());
                result.get(shardNext).add(hashRange);
            }
        }

        return result;
    }

    private CoolPair<Shard, HashRange> addShardByHash(Shard newShard, int vnodeHash) {
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

    private CoolPair<Shard, HashRange> removeShard(Shard shard, int right) {
        CircleRange circleRange = getCircleNext(circle, right);
        CircleRange nextCircleRange = getCircleNext(circle, right + 1);
        HashRange newRange = nextCircleRange.getHashRange().concat(circleRange.getHashRange());

        circle.remove(circleRange);
        circle.remove(nextCircleRange);
        circle.add(new CircleRange(nextCircleRange.getShard(), newRange));

        shardToHashRanges.get(shard).remove(circleRange.getHashRange());
        shardToHashRanges.get(nextCircleRange.getShard()).remove(nextCircleRange.getHashRange());
        shardToHashRanges.get(nextCircleRange.getShard()).add(newRange);

        return new CoolPair<>(nextCircleRange.getShard(), circleRange.getHashRange());
    }

    private static List<Integer> splitAndSort(int elem, List<Integer> list) {
        List<Integer> result = new ArrayList<>();

        List<Integer> result1 = new ArrayList<>();
        List<Integer> result2 = new ArrayList<>();
        for (int hash : list) {
            if (elem < hash) {
                result1.add(hash);
            } else {
                result2.add(hash);
            }
        }
        result.addAll(result1);
        result.addAll(result2);

        return result;
    }

    private CircleRange getLastNotSame(Shard shard) {
        Iterator<CircleRange> it = circle.descendingIterator();
        while (it.hasNext()) {
            CircleRange circleRange = it.next();
            if (!circleRange.getShard().equals(shard)) {
                return circleRange;
            }
        }

        return null;
    }

    private void updateIndexMap() {
        for (int index = 0; index < cluster.size(); index++) {
            shardIndexMap.put(cluster.get(index), index);
        }
    }

    private void addShardEmpty(Shard newShard, List<Integer> vnodeHashes) {
        int first = vnodeHashes.get(vnodeHashes.size() - 1);
        HashRange range = new HashRange(first + 1, first);

        circle.add(new CircleRange(newShard, range));
        shardToHashRanges.put(newShard, new HashSet<>(List.of(range)));

        List<Integer> tail = new ArrayList<>(List.copyOf(vnodeHashes));
        tail.remove(vnodeHashes.size() - 1);

        addShard(newShard, tail);
    }

    private CircleRange getCircleNext(NavigableSet<CircleRange> circle, int e) {
        CircleRange next = circle.ceiling(new CircleRange(new Shard(""), new HashRange(0, e)));
        return next == null ? circle.first() : next;
    }
}
