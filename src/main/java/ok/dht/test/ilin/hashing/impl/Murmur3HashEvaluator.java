package ok.dht.test.ilin.hashing.impl;

import ok.dht.test.ilin.hashing.HashEvaluator;
import one.nio.util.Hash;

public class Murmur3HashEvaluator implements HashEvaluator {
    @Override
    public int hash(byte[] key) {
        return Hash.murmur3(key, 0, key.length);
    }
}
