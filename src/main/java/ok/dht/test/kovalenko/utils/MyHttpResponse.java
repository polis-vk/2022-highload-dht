package ok.dht.test.kovalenko.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.SSLSession;

public class MyHttpResponse implements HttpResponse<byte[]> {

    private final int statusCode;
    private final byte[] body;

    public MyHttpResponse(int statusCode, Exception e) {
        this.statusCode = statusCode;
        this.body = Arrays.toString(e.getStackTrace()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpRequest request() {
        return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return null;
    }

    @Override
    public byte[] body() {
        return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return null;
    }

    @Override
    public HttpClient.Version version() {
        return null;
    }
}
