package ok.dht.test.siniachenko.nodemapper;

import one.nio.util.Utf8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

public class NodeMapper {
    private final ThreadLocal<MessageDigest> hashAlgorithm;
    private final Shard[] shards;

    public NodeMapper(List<String> nodeUrls) {
        try {
            MessageDigest instance = MessageDigest.getInstance("SHA-256");
            hashAlgorithm = ThreadLocal.withInitial(() -> instance);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        shards = new Shard[nodeUrls.size()];
        for (int i = 0; i < shards.length; i++) {
            String url = nodeUrls.get(i);
            byte[] digest = hashAlgorithm.get().digest(Utf8.toBytes(url));
            int nodeHash = hash(digest);
            shards[i] = new Shard(url, nodeHash);
        }
        Arrays.sort(shards);
    }

    public Shard[] getShards() {
        return shards;
    }

    public int getIndexForKey(byte[] key) {
        byte[] digest = hashAlgorithm.get().digest(key);
        int keyHash = hash(digest);
        int index = Arrays.binarySearch(shards, new Shard(null, keyHash));
        if (index < 0) {
            index = -(index + 1);
        }
        return index % shards.length;
    }

    private static int hash(byte[] digest) {
        int hash = 0;
        for (int i = 0; i < 256 / Integer.SIZE; ++i) {
            hash += (digest[i * 4] << 24) + (digest[i * 4 + 1] << 16) + (digest[i * 4 + 2] << 8) + digest[i * 4 + 3];
        }
        return hash;
    }

    public static class Shard implements Comparable<Shard> {
        private final String url;
        private final int hash;

        private Shard(String url, int hash) {
            this.url = url;
            this.hash = hash;
        }

        public int getHash() {
            return hash;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public int compareTo(Shard shard) {
            return Integer.compare(hash, shard.hash);
        }
    }
}
