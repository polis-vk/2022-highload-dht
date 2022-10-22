package ok.dht.test.lutsenko.service;

public class ResponseInfo {
    public static final byte[] EMPTY = new byte[0];
    public final int httpStatusCode;
    public final byte[] body;
    public final Long requestTime;

    public ResponseInfo(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
        this.body = EMPTY;
        this.requestTime = null;
    }
    public ResponseInfo(int httpStatusCode, byte[] body) {
        this.httpStatusCode = httpStatusCode;
        this.body = body;
        this.requestTime = null;
    }

    public ResponseInfo(int httpStatusCode, byte[] body, long requestTime) {
        this.httpStatusCode = httpStatusCode;
        this.body = body;
        this.requestTime = requestTime;
    }

}
