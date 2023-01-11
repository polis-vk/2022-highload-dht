package ok.dht.test.kuleshov;

import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

public class HandleResponse {
    private static final String HTTP_TIMESTAMP_HEADER = "timestamp";
    private static final String ONE_NIO_TIMESTAMP_HEADER = "timestamp:";
    private final byte[] body;
    private final int statusCode;
    private final long timestamp;

    public HandleResponse(byte[] body, int statusCode, long timestamp) {
        this.body = Arrays.copyOf(body, body.length);
        this.statusCode = statusCode;
        this.timestamp = timestamp;
    }

    public static HandleResponse fromHttpResponse(HttpResponse<byte[]> httpResponse) {
        Optional<String> timeStrOpt = httpResponse.headers().firstValue(HTTP_TIMESTAMP_HEADER);
        long time = -1;

        if (timeStrOpt.isPresent()) {
            time = Long.parseLong(timeStrOpt.get());
        }

        return new HandleResponse(httpResponse.body(),
                httpResponse.statusCode(),
                time
        );
    }

    public static HandleResponse fromOneResponse(Response response) {
        final Logger log = LoggerFactory.getLogger("HandleResponseFromOneResponse");
        String timeStr = response.getHeader(ONE_NIO_TIMESTAMP_HEADER);
        long time = -1;

        if (timeStr != null && !timeStr.isBlank()) {
            try {
                time = Long.parseLong(timeStr);
            } catch (NumberFormatException exception) {
                log.warn("Parse timestamp error: " + exception.getMessage());
            }
        }

        return new HandleResponse(response.getBody(),
                response.getStatus(),
                time
        );
    }

    public byte[] getBody() {
        return body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getStringStatusCode() {
        switch (statusCode) {
            case 200 -> {
                return Response.OK;
            }
            case 201 -> {
                return Response.CREATED;
            }
            case 202 -> {
                return Response.ACCEPTED;
            }
            case 404 -> {
                return Response.NOT_FOUND;
            }
            default -> {
                return null;
            }
        }
    }
}
