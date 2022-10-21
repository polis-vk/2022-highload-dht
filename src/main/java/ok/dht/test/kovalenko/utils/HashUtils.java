package ok.dht.test.kovalenko.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public final class HashUtils {

    private HashUtils() {
    }

    public static int getMurmur128Hash(String data) {
        final HashFunction hf = Hashing.murmur3_128();
        final HashCode hc = hf.newHasher().putString(data, Charsets.UTF_8).hash();
        return hc.hashCode();
    }
}
