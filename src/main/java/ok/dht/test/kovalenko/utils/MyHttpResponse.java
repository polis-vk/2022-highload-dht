package ok.dht.test.kovalenko.utils;

import one.nio.http.Response;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MyHttpResponse extends Response {
    private final long timestamp;

    public MyHttpResponse(String resultCode) {
        this(resultCode, 0);
    }

    public MyHttpResponse(String resultCode, long timestamp) {
        this(resultCode, Response.EMPTY, timestamp);
    }

    public MyHttpResponse(String resultCode, Throwable e) {
        this(resultCode, e.toString().getBytes(StandardCharsets.UTF_8), 0);
    }

    public MyHttpResponse(String resultCode, byte[] body, long timestamp) {
        super(resultCode, body);
        this.timestamp = timestamp;
        addHeader(HttpUtils.TIME_HEADER + ":" + timestamp);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static MyHttpResponse notEnoughReplicas() {
        return new MyHttpResponse(HttpUtils.NOT_ENOUGH_REPLICAS, 0);
    }

    public static <R> MyHttpResponse convert(R response) {
        if (response instanceof MyHttpResponse) {
            return (MyHttpResponse) response;
        } else {
            return HttpUtils.toMyHttpResponse((HttpResponse<byte[]>) response);
        }
    }
}
