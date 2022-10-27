package ok.dht.test.skroba.client;

import ok.dht.test.skroba.MyConcurrentHttpServer;
import one.nio.http.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MyClientImpl implements MyClient {
    public static final int TERMINATION_TIME = 1;
    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.of(2, ChronoUnit.SECONDS))
            .build();
    
    @Override
    public CompletableFuture<HttpResponse<byte[]>> sendRequest(
            String uri,
            long timeStamp,
            int method,
            byte[] body
    ) throws URISyntaxException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(uri));
        
        builder.setHeader(MyConcurrentHttpServer.PARENT_REQUEST,
                        MyConcurrentHttpServer.PARENT_REQUEST)
                .setHeader(MyConcurrentHttpServer.TIMESTAMP_REQUEST, Long.toString(timeStamp));
        
        
        HttpRequest httpRequest = switch (method) {
            case Request.METHOD_GET -> builder.GET().build();
            case Request.METHOD_PUT ->
                    builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            case Request.METHOD_DELETE -> builder.DELETE().build();
            default -> throw new IllegalStateException("Method not allowed!");
        };
        
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }
}
