package ok.dht.test.armenakyan.sharding;

import one.nio.http.Request;
import one.nio.http.Response;

import java.io.Closeable;
import java.io.IOException;

public interface ShardRequestHandler extends Closeable {
    Response handleForKey(String key, Request request) throws IOException;
}
