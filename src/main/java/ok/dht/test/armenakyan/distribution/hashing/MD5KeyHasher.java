package ok.dht.test.armenakyan.distribution.hashing;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5KeyHasher implements KeyHasher {
    private final ThreadLocal<MessageDigest> md5;

    public MD5KeyHasher() {
        this.md5 = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new NoSuchAlgorithmRuntimeException(e);
            }
        });
    }

    @Override
    public int hash(byte[] bytes) {
        byte[] digest = md5.get().digest(bytes); // 16 bytes
        ByteBuffer buffer = ByteBuffer.wrap(digest);

        int hash = buffer.getInt();
        for (int i = 0; i < 3; i++) {
            hash ^= buffer.getInt();
        }

        return hash;
    }
}
