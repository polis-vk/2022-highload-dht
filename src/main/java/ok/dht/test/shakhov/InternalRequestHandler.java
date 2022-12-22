package ok.dht.test.shakhov;

import one.nio.http.Request;
import one.nio.http.Response;

public interface InternalRequestHandler {
    Response handleInternalRequest(Request request, String id, long timestamp);
}
