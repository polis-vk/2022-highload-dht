package ok.dht.test.shakhov.http.server;

import one.nio.http.Request;
import one.nio.http.Response;

public interface StreamRequestHandler {
    Response handleStreamRequest(Request request, String start, String end);
}
