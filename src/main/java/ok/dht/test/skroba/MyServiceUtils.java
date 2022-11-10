package ok.dht.test.skroba;

import one.nio.http.HttpServerConfig;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

public final class MyServiceUtils {
    private static final String WRONG_ID = "Id can't be blank or null!";
    
    private MyServiceUtils() {
        // only pr const
    }
    
    static boolean isBadId(String id) {
        return id == null || id.isBlank();
    }
    
    static Response getResponseOnBadId() {
        return new Response(Response.BAD_REQUEST, Utf8.toBytes(WRONG_ID));
    }
    
    static Response getEmptyResponse(String status) {
        return new Response(status, Response.EMPTY);
    }
    
    static Response getResponse(String status, String message) {
        return new Response(status, Utf8.toBytes(message));
    }
    
    static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
