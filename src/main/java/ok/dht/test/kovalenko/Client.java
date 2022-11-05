package ok.dht.test.kovalenko;

import one.nio.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Client {

    private static final java.net.http.HttpClient javaNetClient = java.net.http.HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private String url;

    public void setUrl(String url) {
        this.url = url;
    }

    public HttpResponse<byte[]> get(byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(session, isRequestForReplica).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> put(byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(session, isRequestForReplica).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> delete(byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(session, isRequestForReplica).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpRequest.Builder requestForKey(MyHttpSession session, boolean isRequestForReplica) {
        return request(
                "/v0/entity?id=" + session.getRequestId() + session.getReplicas().toHttpString(),
                isRequestForReplica
        );
    }

    private HttpRequest.Builder request(String path, boolean isRequestForReplica) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + path)).timeout(TIMEOUT);
        if (isRequestForReplica) {
            builder.header("replica", "");
        }
        return builder;
    }

}
