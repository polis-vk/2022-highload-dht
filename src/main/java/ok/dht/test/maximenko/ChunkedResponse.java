package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.Entry;
import one.nio.http.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class ChunkedResponse extends Response {

    private final Iterator<Entry<MemorySegment>> iterator;
    private final String resultCode;

    public boolean last() {
        return !iterator.hasNext();
    }

    public ChunkedResponse(String resultCode) {
        super(resultCode);
        this.resultCode = resultCode;
        this.iterator = null;
    }

    public ChunkedResponse(String resultCode, Iterator<Entry<MemorySegment>> iterator) {
        super(resultCode);
        this.iterator = iterator;
        this.resultCode = resultCode;
    }

    public byte[] getNextChunk() {
        Entry<MemorySegment> entry = iterator.next();

        Response response = new Response(resultCode);
        int chunkSize = (int) (entry.key().byteSize() + Character.SIZE / 8 + entry.value().byteSize());
        response.addHeader("chunk-size: " + chunkSize);

        ByteBuffer body = ByteBuffer.allocate(chunkSize);
        body.put(entry.key().toByteArray());
        body.put("\n".getBytes(StandardCharsets.UTF_8));
        body.put(entry.value().toByteArray());

        response.setBody(body.array());
        return response.toBytes(true);
    }

    public byte[] finalChunk() {
        Response response = new Response(resultCode);
        response.addHeader("chunk-size: 0");
        response.setBody(Response.EMPTY);

        return response.toBytes(true);
    }
}
