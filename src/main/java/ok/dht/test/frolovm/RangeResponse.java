package ok.dht.test.frolovm;

import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

public class RangeResponse extends Response {

    public static final Response ENDING_RESPONSE = new RangeResponse(Response.OK, Utf8.toBytes("0\r\n\r\n"));
    public static final String TRANSFER_ENCODING_CHUNKED = "Transfer-Encoding: chunked";
    private static final byte[] DELIMITER = Utf8.toBytes("\r\n");

    public RangeResponse(String resultCode, byte[] data) {
        super(resultCode, data);
    }

    public static RangeResponse createOneChunk(byte[] data) {
        byte[] size = Utf8.toBytes(Integer.toHexString(data.length));
        ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder(data.length + DELIMITER.length * 2 + size.length);
        byteArrayBuilder.append(size);
        byteArrayBuilder.append(DELIMITER);
        byteArrayBuilder.append(data);
        byteArrayBuilder.append(DELIMITER);
        return new RangeResponse(Response.OK, byteArrayBuilder.toBytes());
    }

    public static Response openChunks() {
        Response openResponse = new Response(Response.OK, Response.EMPTY);
        openResponse.getHeaders()[1] = TRANSFER_ENCODING_CHUNKED;
        return openResponse;
    }

}
