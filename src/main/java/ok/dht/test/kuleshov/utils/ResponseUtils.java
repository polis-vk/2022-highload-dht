package ok.dht.test.kuleshov.utils;

import one.nio.http.Response;

public final class ResponseUtils {
    private ResponseUtils() {

    }

    public static Response emptyResponse(String statusCode) {
        return new Response(statusCode, Response.EMPTY);
    }
}
