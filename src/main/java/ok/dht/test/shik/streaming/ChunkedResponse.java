package ok.dht.test.shik.streaming;

import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;
import org.iq80.leveldb.DBIterator;

import javax.annotation.Nullable;

@SuppressWarnings("PMD.ArrayIsStoredDirectly")
public class ChunkedResponse extends Response {

    private static final byte[] HTTP11_HEADER = Utf8.toBytes("HTTP/1.1 ");
    private static final int PROTOCOL_HEADER_LENGTH = 11;
    private static final String CHUNKED_HEADER = "Transfer-Encoding: chunked";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length: ";

    private DBIterator iterator;

    @Nullable
    private byte[] upperBound;

    public ChunkedResponse(String resultCode, byte[] body) {
        super(resultCode, body);
        addHeader(CHUNKED_HEADER);
    }

    @Override
    public byte[] toBytes(boolean includeBody) {
        String[] headers = getHeaders();
        byte[] body = getBody();
        int estimatedSize = PROTOCOL_HEADER_LENGTH + headers.length * 2;
        for (String header : headers) {
            if (header.startsWith(CONTENT_LENGTH_HEADER)) {
                estimatedSize -= 2;
            } else {
                estimatedSize += header.length();
            }
        }
        if (includeBody && body != null) {
            estimatedSize += body.length;
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(HTTP11_HEADER);
        for (String header : headers) {
            if (!header.startsWith(CONTENT_LENGTH_HEADER)) {
                builder.append(header).append('\r').append('\n');
            }
        }
        builder.append('\r').append('\n');
        if (includeBody && body != null) {
            builder.append(body);
        }
        return builder.toBytes();
    }

    public boolean isLastChunk() {
        return super.getBody().length == 0;
    }

    public DBIterator getIterator() {
        return iterator;
    }

    public void setIterator(DBIterator iterator) {
        this.iterator = iterator;
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    @Nullable
    public byte[] getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(@Nullable byte[] upperBound) {
        this.upperBound = upperBound;
    }
}
