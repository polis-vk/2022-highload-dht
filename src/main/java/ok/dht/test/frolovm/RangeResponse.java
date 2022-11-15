package ok.dht.test.frolovm;

import one.nio.http.Response;
import one.nio.util.Utf8;

import java.nio.ByteBuffer;
import java.util.Map;

public class RangeResponse extends Response {

    private static final byte[] DELIMITER = Utf8.toBytes("\n");

    public static final Response ENDING_RESPONSE = new RangeResponse(Response.OK, Utf8.toBytes("0\r\n\r\n"));

    public RangeResponse(String resultCode, byte[] data) {
        super(resultCode, data);
        getHeaders()[1] = "Transfer-Encoding: chunked";
    }

    public static RangeResponse createOneChunk(Map.Entry<byte[], byte[]> data) {
        int size = data.getKey().length + DELIMITER.length + data.getValue().length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer.put(data.getKey());
        byteBuffer.put(DELIMITER);
        byteBuffer.put(data.getValue());
        return new RangeResponse(Response.OK, byteBuffer.array());
    }

}
