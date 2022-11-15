package ok.dht.test.frolovm;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class Utils {
    public static final int SERVER_ERROR = 500;

    public static final String TIMESTAMP_ONE_NIO = "timestamp: ";

    public static final String TIMESTAMP = "timestamp";

    public static final String NO_SUCH_METHOD = "No such method.";

    public static final String BAD_ID = "Given id is bad.";

    public static final String TOMBSTONE = "TS";

    public static final String TOMBSTONE_ONE_NIO = "TS: ";

    public static final String UNKNOWN_STATUS_CODE = "Unknown status code: ";

    public static final Map<Integer, String> STATUS_MAP = Map.ofEntries(
            Map.entry(100, Response.CONTINUE),
            Map.entry(101, Response.SWITCHING_PROTOCOLS),
            Map.entry(200, Response.OK),
            Map.entry(201, Response.CREATED),
            Map.entry(202, Response.ACCEPTED),
            Map.entry(203, Response.NON_AUTHORITATIVE_INFORMATION),
            Map.entry(204, Response.NO_CONTENT),
            Map.entry(205, Response.RESET_CONTENT),
            Map.entry(206, Response.PARTIAL_CONTENT),
            Map.entry(300, Response.MULTIPLE_CHOICES),
            Map.entry(301, Response.MOVED_PERMANENTLY),
            Map.entry(302, Response.FOUND),
            Map.entry(303, Response.SEE_OTHER),
            Map.entry(304, Response.NOT_MODIFIED),
            Map.entry(305, Response.USE_PROXY),
            Map.entry(307, Response.TEMPORARY_REDIRECT),
            Map.entry(400, Response.BAD_REQUEST),
            Map.entry(401, Response.UNAUTHORIZED),
            Map.entry(402, Response.PAYMENT_REQUIRED),
            Map.entry(403, Response.FORBIDDEN),
            Map.entry(404, Response.NOT_FOUND),
            Map.entry(405, Response.METHOD_NOT_ALLOWED),
            Map.entry(406, Response.NOT_ACCEPTABLE),
            Map.entry(407, Response.PROXY_AUTHENTICATION_REQUIRED),
            Map.entry(408, Response.REQUEST_TIMEOUT),
            Map.entry(409, Response.CONFLICT),
            Map.entry(410, Response.GONE),
            Map.entry(411, Response.LENGTH_REQUIRED),
            Map.entry(412, Response.PRECONDITION_FAILED),
            Map.entry(413, Response.REQUEST_ENTITY_TOO_LARGE),
            Map.entry(414, Response.REQUEST_URI_TOO_LONG),
            Map.entry(415, Response.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(416, Response.REQUESTED_RANGE_NOT_SATISFIABLE),
            Map.entry(417, Response.EXPECTATION_FAILED),
            Map.entry(500, Response.INTERNAL_ERROR),
            Map.entry(501, Response.NOT_IMPLEMENTED),
            Map.entry(502, Response.BAD_GATEWAY),
            Map.entry(503, Response.SERVICE_UNAVAILABLE),
            Map.entry(504, Response.GATEWAY_TIMEOUT),
            Map.entry(505, Response.HTTP_VERSION_NOT_SUPPORTED)
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private Utils() {
    }

    public static boolean checkId(final String id) {
        return id != null && !id.isBlank();
    }

    public static byte[] stringToByte(final String value) {
        return Utf8.toBytes(value);
    }

    public static Response emptyResponse(final String responseCode) {
        return new Response(responseCode, Response.EMPTY);
    }

    public static boolean isServerError(int code) {
        return code >= SERVER_ERROR;
    }

    public static boolean is4xxError(int code) {
        return code >= 400;
    }

    public static void closeExecutorPool(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    LOGGER.error("Pool didn't terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void addTimeStamp(Response response) {
        response.addHeader(Utils.TIMESTAMP_ONE_NIO + System.currentTimeMillis());
    }

    public static void addTimeStamp(Request request) {
        request.addHeader(Utils.TIMESTAMP_ONE_NIO + System.currentTimeMillis());
    }

    public static long getTimestamp(Request request) {
        return Long.parseLong(request.getHeader(Utils.TIMESTAMP_ONE_NIO));
    }

    public static long getTimestamp(Response request) {
        return Long.parseLong(request.getHeader(Utils.TIMESTAMP_ONE_NIO));
    }

    public static boolean isInternal(Request request) {
        return request.getHeader(Utils.TIMESTAMP_ONE_NIO) != null;
    }

    public static boolean isInternal(Response request) {
        return request.getHeader(Utils.TIMESTAMP_ONE_NIO) != null;
    }

    public static byte[] dataToBytes(long timestamp, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        buffer.putLong(timestamp);
        byte[] resultTime = buffer.array();

        byte[] newArray = new byte[(data == null ? 0 : data.length) + resultTime.length + 1];
        System.arraycopy(resultTime, 0, newArray, 0, resultTime.length);
        if (data == null) {
            newArray[resultTime.length] = 1;
        } else {
            newArray[resultTime.length] = 0;
            System.arraycopy(data, 0, newArray, resultTime.length + 1, data.length);
        }
        return newArray;
    }

    public static String getResponseStatus(HttpResponse<byte[]> response) {
        String responseStatus = Utils.STATUS_MAP.get(response.statusCode());
        if (responseStatus == null) {
            throw new IllegalArgumentException(UNKNOWN_STATUS_CODE + response.statusCode());
        }
        return responseStatus;
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException exception) {
            LOGGER.error("Can't send error response ", exception);
        }
    }

    public static int compareArrays(byte[] arrayFirst, byte[] arraySecond) {
        int i = Arrays.mismatch(arrayFirst, arraySecond);
        if (i >= 0 && i < Math.min(arrayFirst.length, arraySecond.length)) {
            return Byte.compare(arrayFirst[i], arraySecond[i]);
        }
        return arrayFirst.length - arraySecond.length;
    }
}
