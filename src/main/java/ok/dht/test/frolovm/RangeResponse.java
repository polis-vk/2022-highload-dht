package ok.dht.test.frolovm;

import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

public class RangeResponse extends Response {

    public static final Response ENDING_RESPONSE = new RangeResponse(Response.OK, Utf8.toBytes("0\r\n\r\n"));
    public static final String TRANSFER_ENCODING_CHUNKED = "Transfer-Encoding: chunked";
    private static final String DELIMITER = "\r\n";
    public static Response OPEN_CHUNKED;

    static {
        OPEN_CHUNKED = new Response(Response.OK, Response.EMPTY);
        OPEN_CHUNKED.getHeaders()[1] = TRANSFER_ENCODING_CHUNKED;
    }

    public RangeResponse(String resultCode, byte[] data) {
        super(resultCode, data);
    }

    public static RangeResponse createOneChunk(byte[] data) {
        ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.append(Integer.toHexString(data.length));
        byteArrayBuilder.append(DELIMITER);
        byteArrayBuilder.append(data);
        byteArrayBuilder.append(DELIMITER);
        return new RangeResponse(Response.OK, byteArrayBuilder.toBytes());
    }

}
