package ok.dht.test.gerasimov.service;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public interface HandleService {

    void handleRequest(Request request, HttpSession session);

    String getEndpoint();
}
