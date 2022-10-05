package ok.dht.test.gerasimov;

import one.nio.http.Response;
import one.nio.util.Utf8;

/**
 * @author Michael Gerasimov
 */
public class ResponseEntity {
    public static Response notFound() {
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    public static Response ok(String message) {
        return new Response(Response.OK, Utf8.toBytes(message));
    }

    public static Response ok(byte[] data) {
        return new Response(Response.OK, data);
    }

    public static Response created() {
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public static Response accepted() {
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    public static Response badRequest(String message) {
        return new Response(Response.BAD_REQUEST, Utf8.toBytes(message));
    }

    public static Response internalError(String message) {
        return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(message));
    }

    public static Response serviceUnavailable() {
        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    }
}
