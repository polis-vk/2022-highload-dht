package ok.dht.test.labazov.hash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hasher {
    private static final Logger LOG = LoggerFactory.getLogger(Hasher.class);
    private final MessageDigest md;

    public Hasher() {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Failed to create SHA-256 hasher: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public int digest(final byte[] input) {
        final byte[] hashBytes = md.digest(input);
        int value = 0;
        for (byte b : hashBytes) {
            value = (value << 8) + (b & 0xFF);
        }
        return value;
    }
}
