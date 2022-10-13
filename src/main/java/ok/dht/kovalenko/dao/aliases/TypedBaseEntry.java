package ok.dht.kovalenko.dao.aliases;

import java.nio.ByteBuffer;

public record TypedBaseEntry(ByteBuffer key, ByteBuffer value) implements TypedEntry {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
