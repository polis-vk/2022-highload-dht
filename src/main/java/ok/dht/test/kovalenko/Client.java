package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.MyHttpSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class Client {

    private static final java.net.http.HttpClient javaNetClient = java.net.http.HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    public CompletableFuture<HttpResponse<byte[]>> get(String url, MyHttpSession session,
                                                       boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(
                url,
                session,
                isRequestForReplica
        ).GET().build();
        return sendAsync(httpRequest);
    }

    public CompletableFuture<HttpResponse<byte[]>> put(String url, byte[] data, MyHttpSession session,
                                                       boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(
                url,
                session,
                isRequestForReplica
        ).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        return sendAsync(httpRequest);
    }

    public CompletableFuture<HttpResponse<byte[]>> delete(String url, MyHttpSession session,
                                                          boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(
                url,
                session,
                isRequestForReplica
        ).DELETE().build();
        return sendAsync(httpRequest);
    }

    private HttpRequest.Builder requestForKey(String url, MyHttpSession session,
                                              boolean isRequestForReplica) {
        return request(
                url,
                "/v0/entity?id=" + session.getRequestId() + session.getReplicas().toHttpString(),
                isRequestForReplica
        );
    }

    private HttpRequest.Builder request(String url, String path,
                                        boolean isRequestForReplica) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + path)).timeout(TIMEOUT);
        if (isRequestForReplica) {
            builder.header("Replica", "");
        }
        return builder;
    }

    private CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
        return javaNetClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    }

}
