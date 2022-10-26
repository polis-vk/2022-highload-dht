package ok.dht.test.vihnin;

import one.nio.http.Request;
import one.nio.http.Response;

public final class ServiceUtils {
    public static final String ENDPOINT = "/v0/entity";

    private ServiceUtils() {

    }

    static Response emptyResponse(String code) {
        return new Response(code, Response.EMPTY);
    }

    static String getHeaderValue(Response response, String headerName) {
        var v = response.getHeader(headerName);
        if (v == null) {
            return null;
        }
        return v.substring(2);
    }

    static String getHeaderValue(Request request, String headerName) {
        var v = request.getHeader(headerName);
        if (v == null) {
            return null;
        }
        return v.substring(2);
    }

}
