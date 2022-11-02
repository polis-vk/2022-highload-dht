package ok.dht.test.kovalenko.dao.aliases;

import java.nio.ByteBuffer;

public record TypedBaseEntry(ByteBuffer key, ByteBuffer value) {
}
