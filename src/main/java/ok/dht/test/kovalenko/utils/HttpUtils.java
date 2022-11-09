package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.Client;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;

public final class HttpUtils {

    public static final Client CLIENT = new Client();
    public static final String REPLICA_HEADER = "Replica";
    public static final String TIME_HEADER = "time";
    public static final String NOT_ENOUGH_REPLICAS = "504 Not enough replicas";

    private HttpUtils() {
    }

    public static String toOneNioResponseCode(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Unknown status code: " + statusCode);
        };
    }

    public static MyHttpResponse toMyHttpResponse(HttpResponse<byte[]> r) {
        String statusCode = toOneNioResponseCode(r.statusCode());
        byte[] body = r.body();
        long time = getTimeHeader(r);
        return new MyHttpResponse(statusCode, body, time);
    }

    public static long getTimeHeader(HttpResponse<byte[]> r) {
        return r.headers().firstValueAsLong(TIME_HEADER)
                .orElseThrow(() -> new IllegalArgumentException("Response " + r + " doesn't contain header 'time'"));
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
