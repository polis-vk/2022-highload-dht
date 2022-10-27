package ok.dht.test.skroba.client;

import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface MyClient {
    int TERMINATION_TIME = 1;
    TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    
    
    CompletableFuture<HttpResponse<byte[]>> sendRequest(
            String uri,
            long timeStamp,
            int method,
            byte[] body
    ) throws URISyntaxException;
}
