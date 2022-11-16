package ok.dht.test.nadutkin.impl.range;

import ok.dht.test.nadutkin.impl.utils.Constants;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;

public class ChunkResponse extends Response {
    private final ByteArrayBuilder buffer;

    public ChunkResponse(String resultCode) {
        super(resultCode);
        buffer = new ByteArrayBuilder();
    }

    public ChunkResponse(String resultCode, byte[] first) {
        this(resultCode);
        buffer.append(first);
    }

    public boolean append(byte[] data) {
        if (data.length + buffer.length() > Constants.CHUNK_SIZE) {
            return false;
        }
        buffer.append(data);
        return true;
    }

    @Override
    public byte[] getBody() {
        return new ByteArrayBuilder().append(Integer.toHexString(buffer.length()))
                .append(Constants.SEPARATOR)
                .append(buffer.toBytes())
                .append(Constants.SEPARATOR)
                .toBytes();
    }

    public int length() {
        return buffer.length();
    }
}
