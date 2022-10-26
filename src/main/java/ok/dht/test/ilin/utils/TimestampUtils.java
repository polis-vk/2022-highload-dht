package ok.dht.test.ilin.utils;

import ok.dht.test.ilin.domain.HeadersUtils;
import one.nio.http.Request;
import one.nio.http.Response;

public class TimestampUtils {
    public static long extractTimestamp(Response response) {
        String header = response.getHeader(HeadersUtils.TIMESTAMP_HEADER);
        return header == null ? Long.MIN_VALUE : Long.parseLong(header);
    }

    public static long extractTimestamp(Request request) {
        String header = request.getHeader(HeadersUtils.TIMESTAMP_HEADER);
        return header == null ? Long.MIN_VALUE : Long.parseLong(header);
    }
}
