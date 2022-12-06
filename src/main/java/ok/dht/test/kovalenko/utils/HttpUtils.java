package ok.dht.test.kovalenko.utils;

import one.nio.http.Response;

import java.net.http.HttpResponse;

public final class HttpUtils {

    private HttpUtils() {
    }

    public static String toOneNio(int statusCode) {
        return switch (statusCode) {
            case 200 -> Response.OK;
            case 201 -> Response.CREATED;
            case 202 -> Response.ACCEPTED;
            case 400 -> Response.BAD_REQUEST;
            case 404 -> Response.NOT_FOUND;
            case 500 -> Response.INTERNAL_ERROR;
            case 503 -> Response.SERVICE_UNAVAILABLE; // reserved
            case 504 -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("No such status code in source project");
        };
    }

    public static Response toOneNio(HttpResponse<byte[]> r) {
        return new Response(toOneNio(r.statusCode()), r.body());
    }

    public static String getResponseCode(Response r) {
        String[] headers = r.getHeaders();
        return headers[0];
    }
}
