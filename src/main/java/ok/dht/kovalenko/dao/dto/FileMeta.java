package ok.dht.kovalenko.dao.dto;

public record FileMeta(byte completelyWritten, byte hasTombstones) {

    public static final byte COMPLETELY_WRITTEN = 1;
    public static final byte INCOMPLETELY_WRITTEN = 0;
    public static final byte HAS_TOMBSTONES = 1;
    public static final byte HAS_NOT_TOMBSTONES = 0;

    public static int size() {
        return Byte.BYTES + Byte.BYTES;
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
}
