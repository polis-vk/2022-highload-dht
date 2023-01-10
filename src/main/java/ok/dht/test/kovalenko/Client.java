package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

public class Client {

    private static final Logger log = LoggerFactory.getLogger(Client.class);
    private static final java.net.http.HttpClient javaNetClient = java.net.http.HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    public MyHttpResponse get(String url, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(url, session, isRequestForReplica).GET().build();
        return safeClientRequest(httpRequest, url);
    }

    public MyHttpResponse put(String url, byte[] data, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(url, session, isRequestForReplica)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        return safeClientRequest(httpRequest, url);
    }

    public MyHttpResponse delete(String url, MyHttpSession session, boolean isRequestForReplica)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = requestForKey(url, session, isRequestForReplica).DELETE().build();
        return safeClientRequest(httpRequest, url);
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
            builder.header("Replica", "");
        }
        return builder;
    }

    private MyHttpResponse safeClientRequest(HttpRequest request, String url) {
        try {
            HttpResponse<byte[]> httpResponse = javaNetClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return HttpUtils.toMyHttpResponse(httpResponse);
        } catch (ConnectException | HttpTimeoutException e) {
            log.error("Unable to connect with node {}", url, e);
            return new MyHttpResponse(
                    Response.GATEWAY_TIMEOUT,
                    Arrays.toString(e.getStackTrace()).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Unexpected error when connecting with node {}", url, e);
            return new MyHttpResponse(
                    Response.INTERNAL_ERROR,
                    Arrays.toString(e.getStackTrace()).getBytes(StandardCharsets.UTF_8)
            );
        }
    }

}
