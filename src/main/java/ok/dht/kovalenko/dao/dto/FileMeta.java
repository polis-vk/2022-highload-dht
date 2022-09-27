package ok.dht.kovalenko.dao.dto;

import ok.dht.kovalenko.dao.DaoFiller;
import ok.dht.kovalenko.dao.base.ByteBufferDaoFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record FileMeta(byte completelyWritten, byte hasTombstones, ByteBufferRange range) {

    public static final byte COMPLETELY_WRITTEN = 1;
    public static final byte INCOMPLETELY_WRITTEN = 0;
    public static final byte HAS_TOMBSTONES = 1;
    public static final byte HAS_NOT_TOMBSTONES = 0;
    private static final ByteBufferDaoFactory daoFactory = new ByteBufferDaoFactory();
    public static final FileMeta DEFAULT_META = ofDefault(daoFactory.fromString(DaoFiller.keyAt(0)));

    public int size() {
        String from = daoFactory.toString(range.from());
        String to = daoFactory.toString(range.to());
        return Byte.BYTES + Byte.BYTES + Integer.BYTES + from.length() + Integer.BYTES + to.length();
    }

    public boolean written() {
        return completelyWritten == COMPLETELY_WRITTEN;
    }

    public boolean notWritten() {
        return !written();
    }

    public boolean tombstoned() {
        return hasTombstones == HAS_TOMBSTONES;
    }

    public boolean notTombstoned() {
        return !tombstoned();
    }

    public byte[] from() {
        return daoFactory.toString(range.from()).getBytes(StandardCharsets.UTF_8);
    }

    public int fromSize() {
        return range.from().rewind().remaining();
    }

    public byte[] to() {
        return daoFactory.toString(range.to()).getBytes(StandardCharsets.UTF_8);
    }

    public int toSize() {
        return range.to().rewind().remaining();
    }

    public static FileMeta ofDefault(ByteBuffer keyType) {
        return new FileMeta(INCOMPLETELY_WRITTEN, HAS_NOT_TOMBSTONES,
                new ByteBufferRange(keyType, keyType));
    }
}
