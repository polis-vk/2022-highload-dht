package ok.dht.test.ponomarev.rest.consts;

import one.nio.http.Response;

public class DefaultResponse {
    public static final Response METHOD_NOT_ALLOWED = new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    public static final Response BAD_REQUEST = new Response(Response.BAD_REQUEST, Response.EMPTY);
    public static final Response SERVICE_UNAVAILABLE = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    public static final Response NOT_FOUND = new Response(Response.NOT_FOUND, Response.EMPTY);
    public static final Response CREATED = new Response(Response.CREATED, Response.EMPTY);
    public static final Response ACCEPTED = new Response(Response.ACCEPTED, Response.EMPTY);
    
    private DefaultResponse() {}
}
