package ok.dht.test.skroba.server.util.handlers;

import one.nio.http.HttpSession;
import one.nio.http.Request;

import java.io.IOException;

@FunctionalInterface
public interface RequestHandler {
    void handle(Request request, HttpSession session, String id) throws IOException;
    
}
