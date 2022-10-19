package ok.dht.test.frolovm;

import jdk.incubator.foreign.MemorySegment;
import one.nio.http.Response;
import one.nio.util.Utf8;
import java.util.Map;

public class Utils {

    private Utils() {
    }

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

    public static boolean checkId(final String id) {
        return id != null && !id.isBlank();
    }

    public static MemorySegment stringToSegment(final String value) {
        return MemorySegment.ofArray(Utf8.toBytes(value));
    }

    public static Response emptyResponse(final String responseCode) {
        return new Response(responseCode, Response.EMPTY);
    }
}
