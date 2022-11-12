package ok.dht.test.kazakov.service.http;

import one.nio.http.Request;

import java.io.IOException;

public interface DaoHttpRequestHandler {
    void handleRequest(Request request, DaoHttpSession session) throws IOException;
}
