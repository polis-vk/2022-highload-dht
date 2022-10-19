package ok.dht.test.skroba.client;

import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public interface MyClient {
    
    CompletableFuture<HttpResponse<byte[]>> sendRequest(String uri, int method, byte[] body) throws URISyntaxException;
}
