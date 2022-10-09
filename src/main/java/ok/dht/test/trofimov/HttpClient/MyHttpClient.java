package ok.dht.test.trofimov.HttpClient;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MyHttpClient {
    private final HttpClient client;

    public MyHttpClient() {
        client = HttpClient.newHttpClient();
    }

    public HttpResponse<byte[]> get(String url, String key) throws IOException, InterruptedException {
        return client.send(
                requestForKey(url, key).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> delete(String url, String key) throws IOException, InterruptedException {
        return client.send(
                requestForKey(url, key).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> upsert(String url, String key, byte[] data) throws IOException, InterruptedException {
        return client.send(
                requestForKey(url, key).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    public HttpResponse<byte[]> post(String url, String key, byte[] data) throws IOException, InterruptedException {
        return client.send(
                requestForKey(url, key).POST(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    HttpRequest.Builder request(String url, String path) {
        return HttpRequest.newBuilder(URI.create(url + path));
    }

    private HttpRequest.Builder requestForKey(String url, String key) {
        return request(url, "/v0/entity?id=" + key);
    }

}
