package ok.dht.test.anikina.utils;

import one.nio.http.Request;
import one.nio.http.Response;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

public class RequestUtils {
    public static final String SYNCHRONIZATION_PATH = "/synchronization";
    private static final int CONNECTION_TIMEOUT_MS = 1000;

    private RequestUtils() {
    }

    public static String matchStatusCode(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            default -> throw new IllegalStateException("Unexpected status code: " + statusCode);
        };
    }

    public static HttpRequest makeHttpRequest(
            String serverUrl, String key, Request request, long timestamp) {
        byte[] body;
        if (request.getMethod() == Request.METHOD_PUT) {
            body = Utils.toByteArray(timestamp, request.getBody());
        } else {
            body = Utils.toByteArray(timestamp);
        }
        String requestPath = SYNCHRONIZATION_PATH + "?id=" + key;
        return HttpRequest.newBuilder(URI.create(serverUrl + requestPath))
                        .method(
                                request.getMethodName(),
                                HttpRequest.BodyPublishers.ofByteArray(body))
                        .timeout(Duration.ofMillis(CONNECTION_TIMEOUT_MS))
                        .build();
    }
}
