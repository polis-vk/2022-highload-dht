package ok.dht.test.siniachenko.nodemapper;

import one.nio.util.Utf8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

public class NodeMapper {
    private final ThreadLocal<MessageDigest> hashAlgorithm;
    public final Shard[] shards;

    public NodeMapper(List<String> nodeUrls) {
        hashAlgorithm = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
        shards = new Shard[nodeUrls.size()];
        for (int i = 0; i < shards.length; i++) {
            String url = nodeUrls.get(i);
            int nodeHash = hash(Utf8.toBytes(url));
            shards[i] = new Shard(url, nodeHash);
        }
        Arrays.sort(shards);
    }

    public int getIndexForKey(byte[] key) {
        int keyHash = hash(key);
        int index = Arrays.binarySearch(shards, new Shard(null, keyHash));
        if (index < 0) {
            index = -(index + 1);
        }
        return index % shards.length;
    }

    private int hash(byte[] data) {
        hashAlgorithm.get().reset();
        byte[] digest = hashAlgorithm.get().digest(data);
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
