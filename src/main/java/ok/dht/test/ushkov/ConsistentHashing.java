package ok.dht.test.ushkov;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsistentHashing {
    private static final int[] SEEDS = new int[]{81, 239, 31, 97, 113};
    private static final int KEY_SEED = 59;

    private final List<Entry> entries = new ArrayList<>();

    public ConsistentHashing() {}

    public String getNodeIdByKey(String key) {
        int hash = hash(key, KEY_SEED);
        int i = 0;
        while (i < entries.size() && hash > entries.get(i).hash){
            i++;
        }
        return entries.get(i % entries.size()).nodeId;
    }

    public void addNode(String nodeId) {
        List<Entry> newEntries = Arrays.stream(SEEDS)
                .mapToObj(seed -> new Entry(nodeId, hash(nodeId, seed)))
                .toList();
        entries.addAll(newEntries);
        entries.sort(Entry::compareTo);
    }

    public void removeNode(String nodeId) {
        entries.removeIf(entry -> entry.nodeId.equals(nodeId));
    }

    private static int hash(String id, int seed) {
        int res = 0;
        for (int i = 0; i < id.length(); i++) {
            res += res * seed + (int) id.charAt(i);
        }
        return res;
    }

    private static class Entry implements Comparable<Entry> {
        private String nodeId;
        private int hash;

        public Entry(String nodeId, int hash) {
            this.nodeId = nodeId;
            this.hash = hash;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public int getHash() {
            return hash;
        }

        public void setHash(int hash) {
            this.hash = hash;
        }

        @Override
        public int compareTo(Entry other) {
            return Integer.compare(hash, other.hash);
        }
    }
}
