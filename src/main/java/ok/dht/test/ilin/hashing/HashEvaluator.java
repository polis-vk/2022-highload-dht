package ok.dht.test.ilin.hashing;

import one.nio.util.Utf8;

@FunctionalInterface
public interface HashEvaluator {
    int hash(byte[] key);

    default int hash(String key) {
        return hash(Utf8.toBytes(key));
    }
}
