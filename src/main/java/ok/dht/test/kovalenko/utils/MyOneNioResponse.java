package ok.dht.test.kovalenko.utils;

import one.nio.http.Response;

public class MyOneNioResponse extends Response {

    private final long entryTimestamp;

    public MyOneNioResponse(String resultCode, byte[] body, long entryTimestamp) {
        super(resultCode, body);
        addHeader("Time: " + entryTimestamp);
        this.entryTimestamp = entryTimestamp;
    }

    public long getEntryTimestamp() {
        return entryTimestamp;
    }
}
