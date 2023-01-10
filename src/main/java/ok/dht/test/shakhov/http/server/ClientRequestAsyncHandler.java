package ok.dht.test.shakhov.http.server;

import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.CompletableFuture;

public interface ClientRequestAsyncHandler {
    CompletableFuture<Response> handleClientRequestAsync(Request request, String id, int ack, int from);
}
