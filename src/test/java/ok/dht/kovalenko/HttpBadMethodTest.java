package ok.dht.kovalenko;

import ok.dht.ServiceInfo;
import ok.dht.ServiceTest;
import ok.dht.TestBase;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpBadMethodTest extends TestBase {

    @ServiceTest(stage = 1)
    void post(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();
        assertEquals(
                HttpURLConnection.HTTP_BAD_METHOD,
                post(service, key, value).statusCode()
        );
    }

    public HttpResponse<byte[]> post(ServiceInfo serviceInfo, String key, byte[] data) throws Exception {
        return client.send(
                requestForKey(serviceInfo, key).POST(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    HttpRequest.Builder request(ServiceInfo serviceInfo, String path) {
        return HttpRequest.newBuilder(URI.create(serviceInfo.url() + path));
    }

    private HttpRequest.Builder requestForKey(ServiceInfo serviceInfo, String key) {
        return request(serviceInfo, "/v0/entity?id=" + key);
    }

}
