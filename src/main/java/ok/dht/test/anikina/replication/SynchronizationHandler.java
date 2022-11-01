package ok.dht.test.anikina.replication;

import ok.dht.test.anikina.utils.Utils;
import one.nio.http.Request;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SynchronizationHandler {
    public static final String SYNCHRONIZATION_PATH = "/synchronization";
    private static final Log log = LogFactory.getLog(SynchronizationHandler.class);

    private static final int CONNECTION_TIMEOUT_MS = 1000;
    private static final Set<Integer> EXPECTED_STATUS_CODES = Set.of(
            200,
            201,
            202,
            404
    );
    private final HttpClient client;

    public SynchronizationHandler() {
        this.client = HttpClient.newHttpClient();
    }

    public List<Response> forwardRequest(String key, Request request, Set<String> nodes, long timestamp) {
        List<Response> responses = new ArrayList<>();
        for (String node : nodes) {
            try {
                HttpResponse<byte[]> httpResponse = proxyRequest(node, key, request, timestamp);
                if (EXPECTED_STATUS_CODES.contains(httpResponse.statusCode())) {
                    responses.add(
                            new Response(
                                    matchStatusCode(httpResponse.statusCode()),
                                    httpResponse.body()
                            )
                    );
                }
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage());
                }
            }
        }
        return responses;
    }

    private HttpResponse<byte[]> proxyRequest(String serverUrl, String key, Request request, long timestamp)
            throws Exception {
        byte[] body;
        if (request.getMethod() == Request.METHOD_PUT) {
            body = Utils.toByteArray(timestamp, request.getBody());
        } else {
            body = Utils.toByteArray(timestamp);
        }
        String requestPath = SYNCHRONIZATION_PATH + "?id=" + key;
        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(serverUrl + requestPath))
                        .method(
                                request.getMethodName(),
                                HttpRequest.BodyPublishers.ofByteArray(body))
                        .timeout(Duration.ofMillis(CONNECTION_TIMEOUT_MS))
                        .build();
        return client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String matchStatusCode(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            default -> throw new IllegalStateException("Unexpected status code: " + statusCode);
        };
    }
}
