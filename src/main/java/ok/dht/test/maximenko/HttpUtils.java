package ok.dht.test.maximenko;

import one.nio.http.Request;
import one.nio.http.Response;

public abstract class HttpUtils {
    private static final String TIME_HEADER = "time";
    
    public static String convertStatusCode(int code) {
        return switch (code) {
            case 200 -> Response.OK;
            case 201 -> Response.CREATED;
            case 500 -> Response.INTERNAL_ERROR;
            case 400 -> Response.BAD_REQUEST;
            case 405 -> Response.METHOD_NOT_ALLOWED;
            case 202 -> Response.ACCEPTED;
            case 404 -> Response.NOT_FOUND;
            case 409 -> Response.CONFLICT;
            default -> Response.BAD_GATEWAY;
        };
    }

    public static boolean isArgumentCorrect(String replicasString) {
        return replicasString != null && !replicasString.equals("");
    }

    public static long getTimeFromResponse(Response response) {
        String timeHeader = response.getHeader(TIME_HEADER + ": ");
        if (timeHeader == null || timeHeader.equals("")) {
            return 0;
        }
        return Long.parseLong(timeHeader);
    }

    public static long getTimeFromRequest(Request request) {
        return Long.parseLong(request.getHeader(TIME_HEADER + ": "));
    }
}
