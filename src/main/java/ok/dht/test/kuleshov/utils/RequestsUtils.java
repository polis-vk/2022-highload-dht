package ok.dht.test.kuleshov.utils;

import one.nio.http.Request;

public final class RequestsUtils {
    private RequestsUtils() {

    }

    public static Integer parseInt(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static long getTimestampHeader(Request request) {
        String timestampString = request.getHeader("timestamp: ");
        long time = -1;
        if (timestampString != null && !timestampString.isBlank()) {
            time = Long.parseLong(timestampString);
        }

        return time;
    }
}
