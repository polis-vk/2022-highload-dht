package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.Client;
import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;

public final class HttpUtils {

    public static final Client CLIENT = Client.INSTANSE;
    public static final String REPLICA_HEADER = "Replica";
    public static final String TIME_HEADER = "time";
    public static final String NOT_ENOUGH_REPLICAS = "504 Not enough replicas";
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 5 * Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 10 * MAX_POOL_SIZE;
    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);
    public static PoolKeeper POOL_KEEPER;

    private HttpUtils() {
    }

    public static void initPoolKeeper() {
        if (POOL_KEEPER == null) {
            POOL_KEEPER = new PoolKeeper(CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, "HttpThread");
        }
    }

    public static ExecutorService getService() {
        return POOL_KEEPER.getService();
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
        long time = getHeader(TIME_HEADER, r);
        return new MyHttpResponse(statusCode, body, time);
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

    public static boolean isGoodResponse(MyHttpResponse response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    public static IdValidation validateId(String id) {
        return id == null || id.isEmpty()
                ? IdValidation.INVALID
                : new IdValidation(true, id);
    }

    public static ReplicasValidation validateReplicas(String ack, String from) {
        try {
            if (ack == null && from == null) {
                return new ReplicasValidation(true, null);
            }

            if (ack == null || from == null) {
                return ReplicasValidation.INVALID;
            }

            int intAck = Integer.parseInt(ack);
            int intFrom = Integer.parseInt(from);

            if (intAck <= 0 || intFrom <= 0 || intAck > intFrom) {
                return ReplicasValidation.INVALID;
            }

            return new ReplicasValidation(true, new Replicas(intAck, intFrom));
        } catch (Exception e) {
            return ReplicasValidation.INVALID;
        }
    }

    public static Replicas recreateReplicas(Replicas replicas, int clusterSize) {
        return replicas == null
                ? new Replicas(clusterSize / 2 + 1, clusterSize)
                : replicas;
    }

    public static RangeValidation validateRange(String start, String end) {
        try {
            if (start == null || (end != null && start.compareTo(end) >= 0)) {
                return RangeValidation.INVALID;
            }

            return new RangeValidation(true, new Range(start, end));
        } catch (Exception e) {
            return RangeValidation.INVALID;
        }
    }

    public record IdValidation(boolean valid, String id) {
        public static final IdValidation INVALID = new IdValidation(false, null);
    }

    public record Replicas(int ack, int from) {
        public String toHttpString() {
            return "&ack=" + ack + "&from=" + from;
        }
    }

    public record ReplicasValidation(boolean valid, Replicas replicas) {
        public static final ReplicasValidation INVALID = new ReplicasValidation(false, null);
    }

    public record Range(String start, String end) {
    }

    public record RangeValidation(boolean valid, Range range) {
        public static final RangeValidation INVALID = new RangeValidation(false, null);
    }

    private static long getHeader(String header, HttpResponse<byte[]> r) {
        return r.headers().firstValueAsLong(header)
                .orElseThrow(() ->
                        new IllegalArgumentException("Response " + r + " doesn't contain header " + header));
    }

    public interface NetRequest {
        void execute() throws Exception;
    }

}
