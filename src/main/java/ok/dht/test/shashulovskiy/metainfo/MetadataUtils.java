package ok.dht.test.shashulovskiy.metainfo;

import com.google.common.primitives.Longs;

public final class MetadataUtils {

    // We store 1 long for timestamp and 1 byte got tombstone flag
    private static final int METADATA_SIZE = Long.BYTES + 1;

    private MetadataUtils() {
        // Utils class
    }

    public static byte[] wrapWithMetadata(byte[] s, boolean isTombstone) {
        long currentTimestamp = System.currentTimeMillis();
        byte[] result = new byte[s.length + METADATA_SIZE];

        // Write timestamp to first 8 bytes
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (currentTimestamp & 0xFFL);
            currentTimestamp >>= 8;
        }

        // Leave 9th byte for tombstone metadata and write the rest with actual data
        result[METADATA_SIZE - 1] |= isTombstone ? 1 : 0;
        System.arraycopy(s, 0, result, METADATA_SIZE, s.length);

        return result;
    }

    public static long extractTimestamp(byte[] body) {
        return Longs.fromByteArray(body);
    }

    public static boolean isTombstone(byte[] lastResponse) {
        return (lastResponse[METADATA_SIZE - 1] & 1) == 1;
    }

    public static byte[] extractData(byte[] lastResponse) {
        byte[] data = new byte[lastResponse.length - METADATA_SIZE];
        System.arraycopy(lastResponse, METADATA_SIZE, data, 0, lastResponse.length - METADATA_SIZE);

        return data;
    }
}
