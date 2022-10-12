package ok.dht.test.armenakyan.sharding.hashing;

import one.nio.util.Utf8;

public interface KeyHasher {
    int hash(byte[] bytes);

    default int hash(String key) {
        return hash(Utf8.toBytes(key));
    }
}
