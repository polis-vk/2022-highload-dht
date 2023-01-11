package ok.dht.test.shakhov.http.response;

import one.nio.http.Response;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

import static ok.dht.test.shakhov.http.HttpUtils.ONE_NIO_X_RECORD_TIMESTAMP_HEADER;
import static ok.dht.test.shakhov.http.HttpUtils.X_RECORD_TIMESTAMP_HEADER;

public class ResponseWithTimestamp {

    private final int statusCode;
    private final byte[] body;
    private final long timestamp;

    public ResponseWithTimestamp(Response oneNioHttpResponse) {
        this.statusCode = oneNioHttpResponse.getStatus();
        this.body = oneNioHttpResponse.getBody();
        String timestampHeader = oneNioHttpResponse.getHeader(ONE_NIO_X_RECORD_TIMESTAMP_HEADER);
        this.timestamp = timestampHeader == null ? Long.MIN_VALUE : Long.parseLong(timestampHeader);
    }

    public ResponseWithTimestamp(HttpResponse<byte[]> javaHttpResponse) {
        this.statusCode = javaHttpResponse.statusCode();
        this.body = javaHttpResponse.body();
        Optional<String> headerValue = javaHttpResponse.headers().firstValue(X_RECORD_TIMESTAMP_HEADER);
        this.timestamp = headerValue.map(Long::parseLong).orElse(Long.MIN_VALUE);
    }

    public int statusCode() {
        return statusCode;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public long timestamp() {
        return timestamp;
    }
}
