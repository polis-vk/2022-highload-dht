package ok.dht.test.ushkov;

import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;

public final class Chunk extends Response {
    private Chunk(byte[] body) {
        super(OK, body);
    }

    public static Chunk newChunk(byte[] body) {
        ByteArrayBuilder builder = new ByteArrayBuilder();
        builder.append(Integer.toHexString(body.length))
                .append('\r').append('\n')
                .append(body)
                .append('\r').append('\n');
        return new Chunk(builder.toBytes());
    }

    public static Chunk newLastChunk() {
        return new Chunk(new byte[]{'0', '\r', '\n', '\r', '\n'});
    }
}
