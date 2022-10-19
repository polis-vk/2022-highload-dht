package ok.dht.test.armenakyan.sharding;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.Closeable;
import java.io.IOException;

public interface ShardRequestHandler extends Closeable {
    Response handleForKey(String key, Request request) throws IOException;

    void handleForKey(String key, Request request, HttpSession session) throws IOException;

    @Override
    default void close() throws IOException {
        //nothing to close by default
    }
}
