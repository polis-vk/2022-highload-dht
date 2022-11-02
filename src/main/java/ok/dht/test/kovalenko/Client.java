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

    public HttpResponse<byte[]> get(String key, byte[] data, MyHttpSession session) throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(key, session).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> put(String key, byte[] data, MyHttpSession session) throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(key, session).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> delete(String key, byte[] data, MyHttpSession session) throws IOException, InterruptedException {
        return javaNetClient.send(
                requestForKey(key, session).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpRequest.Builder requestForKey(String key, MyHttpSession session) {
        return request("/v0/entity?id=" + key + "&replicas=" + session.getReplicas().toString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(url + path)).timeout(TIMEOUT);
    }

}
