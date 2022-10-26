package ok.dht.test.mikhaylov;

import one.nio.http.Request;


public class DatabaseUtilities {
    private DatabaseUtilities() {
    }

    public static Request attachMeta(Request request) {
        byte[] body = request.getBody();
        byte[] valueWithMeta = new byte[body.length + Long.BYTES + 1]; // timestamp + tombstone
        valueWithMeta[0] = 0; // not a tombstone
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < Long.BYTES; i++) {
            valueWithMeta[i + 1] = (byte) (timestamp >> (i * Byte.SIZE));
        }
        System.arraycopy(body, 0, valueWithMeta, Long.BYTES + 1, body.length);
        Request newRequest = new Request(request.getMethod(), request.getURI(), request.isHttp11());
        newRequest.setBody(valueWithMeta);
        for (String header : request.getHeaders()) {
            if (header != null && !header.startsWith("Content-Length")) {
                newRequest.addHeader(header);
            }
        }
        newRequest.addHeader("Content-Length: " + valueWithMeta.length);
        return newRequest;
    }

    public static byte[] markAsTombstone(byte[] valueWithMeta) {
        valueWithMeta[0] = 1; // tombstone
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < Long.BYTES; i++) {
            valueWithMeta[i + 1] = (byte) (timestamp >> (i * Byte.SIZE));
        }
        return valueWithMeta;
    }

    public static long getTimestamp(byte[] value) {
        long timestamp = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            timestamp |= (value[i + 1] & 0xFFL) << (i * Byte.SIZE);
        }
        return timestamp;
    }

    public static boolean isTombstone(byte[] value) {
        return value[0] == 1;
    }

    public static byte[] getValue(byte[] valueWithMeta) {
        byte[] value = new byte[valueWithMeta.length - Long.BYTES - 1];
        System.arraycopy(valueWithMeta, Long.BYTES + 1, value, 0, value.length);
        return value;
    }
}
