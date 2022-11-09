package ok.dht.test.skroba.server.util.handlers;

import one.nio.http.HttpSession;
import one.nio.http.Request;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@FunctionalInterface
public interface RequestHandler {
    void handle(Request request, HttpSession session, String id, ExecutorService service) throws IOException;
    
}
