package ok.dht.test.kovalenko;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class ServiceClient {

    private static final java.net.http.HttpClient javaNetClient = java.net.http.HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private String url;

    public void setUrl(String url) {
        this.url = url;
    }

    public HttpResponse<byte[]> get(String key) throws ExecutionException, InterruptedException {
        return javaNetClient.sendAsync(
                requestForKey(key).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).get();
    }

    public HttpResponse<byte[]> put(String key, byte[] data) throws ExecutionException, InterruptedException {
        return javaNetClient.sendAsync(
                requestForKey(key).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        ).get();
    }

    public HttpResponse<byte[]> delete(String key) throws ExecutionException, InterruptedException {
        return javaNetClient.sendAsync(
                requestForKey(key).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        ).get();
    }

    private HttpRequest.Builder requestForKey(String key) {
        return request("/v0/entity?id=" + key);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(url + path)).timeout(TIMEOUT);
    }
}
