package ok.dht.test.shashulovskiy.hashing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Hasher implements Hasher {
    private final ThreadLocal<MessageDigest> md;

    public MD5Hasher() {
        this.md = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                // Should be unreachable
                throw new RuntimeException("MD5 Hash not found?");
            }
        });

    }

    public long getHash(byte[] bytes) {
        byte[] digest = md.get().digest(bytes);

        // Take first 8 bytes to get 64-bit long
        return ((digest[0] & 0xFFL) << 56)
                | ((digest[1] & 0xFFL) << 48)
                | ((digest[2] & 0xFFL) << 40)
                | ((digest[3] & 0xFFL) << 32)
                | ((digest[4] & 0xFFL) << 24)
                | ((digest[5] & 0xFFL) << 16)
                | ((digest[6] & 0xFFL) << 8)
                | (digest[7] & 0xFFL);
    }
}
