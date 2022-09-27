package ok.dht.kovalenko.dao.base;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.LSMDao;
import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@DaoFactory(stage = 5000, week = 1)
public class ByteBufferDaoFactory implements DaoFactory.Factory<ByteBuffer, TypedEntry> {

    @Override
    public Dao<ByteBuffer, TypedEntry> createDao(ServiceConfig config) throws IOException {
        return new LSMDao(config);
    }

    @Override
    public String toString(ByteBuffer data) {
        ByteBuffer transfer = data.rewind().asReadOnlyBuffer();
        byte[] bytes = new byte[transfer.remaining()];
        transfer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public ByteBuffer fromString(String data) {
        return data == null ? null : ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public TypedBaseEntry fromBaseEntry(Entry<ByteBuffer> baseEntry) {
        return new TypedBaseEntry(baseEntry.key(), baseEntry.value());
    }
}
