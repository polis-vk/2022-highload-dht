package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.Entry;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;
import java.util.Iterator;

public class ChunkedResponse extends Response {

    private final Iterator<Entry<MemorySegment>> iterator;
    private final String resultCode;

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public ChunkedResponse(String resultCode, Iterator<Entry<MemorySegment>> iterator) {
        super(resultCode);
        this.iterator = iterator;
        this.resultCode = resultCode;
    }

    public byte[] initialChunk() {
        Response response = new Response(resultCode);
        response.addHeader("Transfer-Encoding: chunked");
        response.addHeader("Content-Type: text/plain");
        response.addHeader("Connection: keep-alive");

        return response.toBytes(true);
    }

    public byte[] getNextChunk() {
        Entry<MemorySegment> entry = iterator.next();

        byte[] key  = entry.key().toByteArray();
        byte[] value = entry.value().toByteArray();
        int chunkSize = key.length + 1 + value.length;
        String chunkSizeString = Integer.toHexString(chunkSize);
        int estimatedSize = chunkSizeString.length()  + 2 + chunkSize + 2;

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(chunkSizeString);
        builder.append('\r').append('\n');

        builder.append(key);
        builder.append('\n');
        builder.append(value);
        builder.append('\r').append('\n');

        return builder.buffer();
    }

    public byte[] finalChunk() {
        int estimatedSize = 5;
        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        String chunkSizeString = Integer.toHexString(0);
        builder.append(chunkSizeString);
        builder.append('\r').append('\n');
        builder.append('\r').append('\n');

        return builder.buffer();
    }
}
