package ok.dht.test.kiselyov.util;

import one.nio.http.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class InternalClient {
    private final HttpClient client;

    public InternalClient() {
        client = HttpClient.newHttpClient();
    }

    public CompletableFuture<HttpResponse<byte[]>> sendRequestToNode(Request request, ClusterNode clusterNode, String id)
            throws URISyntaxException {
        return client.sendAsync(resendRequest(request, clusterNode, id), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest resendRequest(Request request, ClusterNode clusterNode, String id) throws URISyntaxException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(clusterNode.getUrl() + "/v0/entity?id=" + id));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> builder.GET();
            case Request.METHOD_PUT -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> builder.DELETE();
            default -> throw new InternalError("Unsupported request method: " + request.getMethodName());
        }
        return builder.build();
    }
}
