package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.dao.base.DaoFactoryB;
import ok.dht.test.kovalenko.utils.HttpUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

public class Client {

    private static final java.net.http.HttpClient javaNetClient = java.net.http.HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    public HttpResponse<byte[]> get(String url, byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        try {
            return javaNetClient.send(
                    requestForKey(url, session, isRequestForReplica).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
        } catch (ConnectException | HttpTimeoutException e) {
            return gatewayResponse(e);
        }
    }

    public HttpResponse<byte[]> put(String url, byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        try {
            return javaNetClient.send(
                    requestForKey(url, session, isRequestForReplica).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
        } catch (ConnectException | HttpTimeoutException e) {
            return gatewayResponse(e);
        }
    }

    public HttpResponse<byte[]> delete(String url, byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        try {
            return javaNetClient.send(
                    requestForKey(url, session, isRequestForReplica).DELETE().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
        } catch (ConnectException | HttpTimeoutException e) {
            return gatewayResponse(e);
        }
    }

    private HttpRequest.Builder requestForKey(String url, MyHttpSession session, boolean isRequestForReplica) {
        return request(
                url,
                "/v0/entity?id=" + session.getRequestId() + session.getReplicas().toHttpString(),
                isRequestForReplica
        );
    }

    private HttpRequest.Builder request(String url, String path, boolean isRequestForReplica) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + path)).timeout(TIMEOUT);
        if (isRequestForReplica) {
            builder.header("replica", "");
        }
        return builder;
    }

    private static HttpResponse<byte[]> gatewayResponse(Exception e) {
        return new HttpUtils.MyHttpResponse(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, e);
    }

}
