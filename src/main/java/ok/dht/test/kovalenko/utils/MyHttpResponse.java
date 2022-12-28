package ok.dht.test.kovalenko.utils;

import one.nio.http.Response;

public class MyHttpResponse extends Response {
    private final long timestamp;

    public MyHttpResponse(String resultCode, long timestamp) {
        this(resultCode, Response.EMPTY, timestamp);
    }

    public MyHttpResponse(String resultCode, byte[] body) {
        this(resultCode, body, 0);
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
}
