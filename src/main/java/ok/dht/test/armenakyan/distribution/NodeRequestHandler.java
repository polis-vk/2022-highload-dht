package ok.dht.test.armenakyan.distribution;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface NodeRequestHandler extends Closeable {
    void handleForKey(String key, Request request, HttpSession session, long timestamp) throws IOException;

    CompletableFuture<Response> handleForKeyAsync(String key, Request request, long timestamp);

    @Override
    default void close() throws IOException {
        //nothing to close by default
    }
}
