package ok.dht.test.shik.sharding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

public class ConsistentHash {

    private static final Log LOG = LogFactory.getLog(ConsistentHash.class);
    private static final String HASHING_ALGORITHMS = "SHA-256";

    // MessageDigest is not thread safe
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(HASHING_ALGORITHMS);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Cannot instantiate sha-256 algorithm", e);
            throw new RuntimeException("Cannot instantiate sha-256 algorithm", e);
        }
    });

    private final int[] hashes;
    private final int[] nodeIndex;
    private final List<String> clusterUrls;

    public ConsistentHash(int virtualNodesNumber, List<String> clusterUrls) {
        int length = clusterUrls.size() * virtualNodesNumber;
        hashes = new int[length];
        nodeIndex = new int[length];
        this.clusterUrls = clusterUrls;

        PriorityQueue<Pair> queue = new PriorityQueue<>(length,
            Comparator.comparingInt(Pair::getKey).thenComparing(Pair::getIndex));
        for (int i = 0; i < clusterUrls.size(); ++i) {
            String url = clusterUrls.get(i);
            for (int j = 0; j < virtualNodesNumber; ++j) {
                byte[] hash = DIGEST.get().digest((url + j + url).getBytes(StandardCharsets.UTF_8));
                queue.add(new Pair(convertToIntHash(hash), i));
            }
        }

        for (int i = 0; i < length; ++i) {
            Pair pair = queue.poll();
            if (pair == null) {
                LOG.error("Error while sorting hashes in consistent hashing, cannot poll all hashes");
                throw new IllegalStateException("Error while sorting hashes in consistent hashing,"
                    + " cannot poll all hashes");
            }

            hashes[i] = pair.key;
            nodeIndex[i] = pair.index;
        }
    }

    public List<String> getShardUrlByKey(byte[] key, int nodesCount) {
        byte[] hash = DIGEST.get().digest(key);
        int insertionPoint = Arrays.binarySearch(hashes, convertToIntHash(hash));
        if (insertionPoint >= 0) {
            return getFromOffset(insertionPoint, nodesCount);
        }
        insertionPoint = -insertionPoint - 2;
        if (insertionPoint >= 0) {
            return getFromOffset(insertionPoint, nodesCount);
        }
        return getFromOffset(nodeIndex.length - 1, nodesCount);
    }

    private List<String> getFromOffset(int offset, int nodesCount) {
        Set<Integer> nodes = new HashSet<>(nodesCount);
        int i = offset;
        while (nodes.size() < nodesCount) {
            nodes.add(nodeIndex[i]);
            i = (i + 1) % nodeIndex.length;
        }
        List<String> result = new ArrayList<>(nodesCount);
        for (int nodeId : nodes) {
            result.add(clusterUrls.get(nodeId));
        }
        return result;
    }

    private int convertToIntHash(byte[] sha256) {
        return (sha256[0] << 24) + (sha256[1] << 16) + (sha256[2] << 8) + sha256[3];
    }

    private static class Pair {
        private final int key;
        private final int index;

        public Pair(int key, int index) {
            this.key = key;
            this.index = index;
        }

        public int getKey() {
            return key;
        }

        public int getIndex() {
            return index;
        }
    }
}

