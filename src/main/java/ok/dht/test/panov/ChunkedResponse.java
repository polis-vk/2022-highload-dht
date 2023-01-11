package ok.dht.test.panov;

import jdk.incubator.foreign.MemorySegment;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;

public class ChunkedResponse extends Response {

    static final ChunkedResponse END_CHUNK = new ChunkedResponse(OK, "0\r\n\r\n".getBytes(StandardCharsets.UTF_8));

    public ChunkedResponse(String resultCode, byte[] body) {
        super(resultCode, body);
    }

    static class ChunkBuilder {
        private static final int CHUNK_SIZE = 1024;

         private final ByteArrayBuilder bytes = new ByteArrayBuilder(CHUNK_SIZE);

        public ChunkBuilder addElement(MemorySegment key, MemorySegment value) {
            bytes.append(key.toByteArray());
            bytes.append('\n');
            bytes.append(value.toByteArray());
            return this;
        }

        public boolean isFull(MemorySegment key, MemorySegment value) {
            long elementSize = key.byteSize() + value.byteSize() + 1;
            return bytes.length() + elementSize > CHUNK_SIZE;
        }

        public ChunkedResponse build() {
            final ByteArrayBuilder result = new ByteArrayBuilder();
            result.append(Integer.toHexString(bytes.length()))
                    .append("\r\n").append(bytes.toBytes())
                    .append("\r\n");
            return new ChunkedResponse(OK, result.toBytes());
        }

        public int length() {
            return bytes.length();
        }
    }
}
