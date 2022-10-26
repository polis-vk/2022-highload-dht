package ok.dht.test.ilin.utils;

import ok.dht.test.ilin.domain.Headers;
import one.nio.http.Request;
import one.nio.http.Response;

public final class TimestampUtils {
    private TimestampUtils() {

    }

    public static long extractTimestamp(Response response) {
        String header = response.getHeader(Headers.TIMESTAMP_HEADER);
        return header == null ? Long.MIN_VALUE : Long.parseLong(header);
    }

    public static long extractTimestamp(Request request) {
        String header = request.getHeader(Headers.TIMESTAMP_HEADER);
        return header == null ? Long.MIN_VALUE : Long.parseLong(header);
    }
}
