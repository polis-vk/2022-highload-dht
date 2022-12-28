package ok.dht.test.kovalenko.dao.aliases;

import java.nio.ByteBuffer;

public record TypedBaseTimedEntry(long timestamp, ByteBuffer key, ByteBuffer value) implements TypedTimedEntry {
    public static TypedBaseEntry withoutTime(TypedTimedEntry entry) {
        return entry == null
                ? null
                : new TypedBaseEntry(entry.key(), entry.value());
    }

    @Override
    public String toString() {
        return "{time = " + timestamp + ", key = " + key + ", value = " + value + "}";
    }
}
