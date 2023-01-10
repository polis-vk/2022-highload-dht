package ok.dht.test.vihnin;

import one.nio.http.Response;

public final class ServiceUtils {
    public static final String ENDPOINT = "/v0/entity";

    private ServiceUtils() {

    }

    static Response emptyResponse(String code) {
        return new Response(code, Response.EMPTY);
    }
}
