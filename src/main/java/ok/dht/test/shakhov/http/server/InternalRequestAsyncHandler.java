package ok.dht.test.shakhov.http.server;

import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.CompletableFuture;

public interface InternalRequestAsyncHandler {
    CompletableFuture<Response> handleInternalRequestAsync(Request request, String id, long timestamp);
}
