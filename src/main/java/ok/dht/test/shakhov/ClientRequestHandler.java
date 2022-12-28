package ok.dht.test.shakhov;

import one.nio.http.Request;
import one.nio.http.Response;

public interface ClientRequestHandler {
    Response handleClientRequest(Request request, String id, int ack, int from);
}
