package ok.dht.test.shik.sharding;

import ok.dht.test.shik.ServiceImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingAlgorithm {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);
    private static final String HASHING_ALGORITHMS = "SHA-256";

    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(HASHING_ALGORITHMS);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Cannot instantiate sha-256 algorithm", e);
            throw new RuntimeException("Cannot instantiate sha-256 algorithm", e);
        }
    });

    public byte[] hash(byte[] value) {
        return DIGEST.get().digest(value);
    }
}
