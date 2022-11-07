package ok.dht.test.kovalenko.dao.base;

import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@DaoFactoryB(stage = 5000, week = 1)
public class ByteBufferDaoFactoryB implements DaoFactoryB.Factory<ByteBuffer, TypedTimedEntry> {

    @Override
    public Dao<ByteBuffer, TypedTimedEntry> createDao(ServiceConfig config) throws IOException {
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
    public TypedBaseTimedEntry fromBaseEntry(TimedEntry<ByteBuffer> baseTimedEntry) {
        return new TypedBaseTimedEntry(baseTimedEntry.timestamp(), baseTimedEntry.key(), baseTimedEntry.value());
    }

    @Override
    public byte[] toBytes(ByteBuffer bb) {
        byte[] bytes = new byte[bb.rewind().remaining()];
        bb.get(bytes);
        return bytes;
    }

}
