package ok.dht.test.ilin.hashing.impl;

import ok.dht.test.ilin.hashing.HashEvaluator;

public class FNV32HashEvaluator implements HashEvaluator {
    private static final int FNV_32_INIT = 0x811c9dc5;
    private static final int FNV_32_PRIME = 0x01000193;

    @Override
    public int hash(byte[] key) {
        int rv = FNV_32_INIT;
        for (final byte b : key) {
            rv ^= b;
            rv *= FNV_32_PRIME;
        }
        return rv;
    }
}
