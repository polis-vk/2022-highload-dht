package ok.dht.test.kovalenko.utils;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.http.HttpResponse;

public final class HttpUtils {

    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private HttpUtils() {
    }

    public static String toOneNio(int statusCode) {
        return switch (statusCode) {
            case 200 -> Response.OK;
            case 201 -> Response.CREATED;
            case 202 -> Response.ACCEPTED;
            case 400 -> Response.BAD_REQUEST;
            case 404 -> Response.NOT_FOUND;
            case 500 -> Response.INTERNAL_ERROR;
            case 503 -> Response.SERVICE_UNAVAILABLE;
            case 504 -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("No such status code in source project");
        };
    }

    public static Response toOneNio(HttpResponse<byte[]> r) {
        return new Response(toOneNio(r.statusCode()), r.body());
    }

    public static String getResponseCode(Response r) {
        String[] headers = r.getHeaders();
        return headers[0];
    }

    public static void sendError(String responseCode, Exception e, HttpSession session, Logger log) {
        try {
            log.error("Unexpected error", e);
            session.sendError(responseCode, e.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error, unable to send error", ex);
            session.close();
        }
    }

    public static void safeHttpRequest(HttpSession session, Logger log, NetRequest netRequest) {
        try {
            netRequest.execute();
        } catch (IOException ex) {
            sendError(Response.SERVICE_UNAVAILABLE, ex, session, log);
        } catch (Exception ex) {
            log.error("Fatal error", ex);
        }
    }

    public interface NetRequest {
        void execute() throws Exception;
    }

}
