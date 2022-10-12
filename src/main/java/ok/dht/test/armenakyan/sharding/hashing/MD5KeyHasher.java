package ok.dht.test.armenakyan.sharding.hashing;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5KeyHasher implements KeyHasher {
    private final MessageDigest md5;

    public MD5KeyHasher() throws NoSuchAlgorithmException {
        this.md5 = MessageDigest.getInstance("MD5");
    }

    @Override
    public int hash(byte[] bytes) {
        byte[] digest = md5.digest(bytes); // 16 bytes
        ByteBuffer buffer = ByteBuffer.wrap(digest);

        int hash = buffer.getInt();
        for (int i = 0; i < 3; i++) {
            hash ^= buffer.getInt();
        }

        return hash;
    }
}
