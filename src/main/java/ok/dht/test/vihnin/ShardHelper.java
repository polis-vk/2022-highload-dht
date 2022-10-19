package ok.dht.test.vihnin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ShardHelper {
    private final Map<Integer, Integer> nodeToShard = new HashMap<>();
    private final Map<Integer, Set<Integer>> shardToNodes = new HashMap<>();
    private final NavigableSet<Integer> nodeSet = new TreeSet<>();

    public Integer getShardByKey(String key) {
        Integer nextShard = nextOrEquals(key.hashCode());
        return nodeToShard.get(nextShard == null ? nodeSet.first() : nextShard);
    }

    public Map<Integer, Set<HashRange>> addShard(Integer newShard, Set<Integer> vnodeHashes) {
        boolean init = nodeSet.isEmpty();

        addNewVNodes(newShard, vnodeHashes);

        if (init) return new HashMap<>();

        Map<Integer, Set<HashRange>> result = new HashMap<>();

        var leftBorder = nodeSet.first();
        var rightBorder = nodeSet.last();

        var visitedHashes = new HashMap<Integer, Boolean>();
        for (var v: vnodeHashes) {
            visitedHashes.put(v, false);
        }

        findRange(newShard, vnodeHashes, result, List.of(leftBorder, rightBorder), visitedHashes);

        return result;
    }

    private void addNewVNodes(Integer newShard, Set<Integer> vnodeHashes) {
        nodeSet.addAll(vnodeHashes);

        for (var v: vnodeHashes) {
            nodeToShard.put(v, newShard);
        }

        shardToNodes.put(newShard, vnodeHashes);
    }

    private void findRange(
            Integer newShard,
            Set<Integer> vnodeHashes,
            Map<Integer, Set<HashRange>> result,
            List<Integer> borders,
            Map<Integer, Boolean> visitedHashes) {
        for (var hash: vnodeHashes) {
            if (visitedHashes.get(hash)) continue;

            var right = hash;
            var prevRight = right;

            while (nodeToShard.get(right).equals(newShard)) {
                visitedHashes.put(right, true);
                prevRight = right;
                right = next(right);
                if (right == null) {
                    right = borders.get(0);
                }
            }

            var left = hash;

            while (nodeToShard.get(left).equals(newShard)) {
                visitedHashes.put(left, true);
                left = prev(left);
                if (left == null) {
                    left = borders.get(1);
                }
            }

            var rightShard = nodeToShard.get(right);

            if (!result.containsKey(rightShard)) {
                result.put(rightShard, new HashSet<>());
            }

            result.get(rightShard).add(new HashRange(left + 1, prevRight));
        }
    }

    private Integer next(Integer hash) {
        Integer res = nodeSet.ceiling(hash);
        if (res == null) return null;
        if (res.equals(hash)) return nodeSet.ceiling(hash + 1);
        return res;
    }

    private Integer nextOrEquals(Integer hash) {
        return nodeSet.ceiling(hash);
    }

    private Integer prev(Integer hash) {
        Integer res = nodeSet.floor(hash);
        if (res == null) return null;
        if (res.equals(hash)) return nodeSet.floor(hash - 1);
        return res;
    }

    public static class HashRange {
        public final Integer leftBorder;
        public final Integer rightBorder;

        public HashRange(Integer leftBorder, Integer rightBorder) {
            this.leftBorder = leftBorder;
            this.rightBorder = rightBorder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashRange hashRange = (HashRange) o;
            return Objects.equals(leftBorder, hashRange.leftBorder)
                    && Objects.equals(rightBorder, hashRange.rightBorder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(leftBorder, rightBorder);
        }
    }
}
