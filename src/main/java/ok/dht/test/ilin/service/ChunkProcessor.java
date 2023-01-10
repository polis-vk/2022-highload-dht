package ok.dht.test.ilin.service;

import ok.dht.test.ilin.domain.Entity;
import ok.dht.test.ilin.domain.Serializer;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ChunkProcessor {
    private static final byte[] END_RESPONSE = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEPARATE = "\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEPARATE_RESPONSES = "\r\n".getBytes(StandardCharsets.UTF_8);
    private final RocksIterator iterator;
    private final String end;
    private boolean processedAll = false;
    private int stage = 0;

    public ChunkProcessor(RocksIterator iterator, String end) {
        this.iterator = iterator;
        this.end = end;
    }

    public byte[] process() {
        if (processedAll) {
            return null;
        }
        if (!iterator.isValid()) {
            processedAll = true;
            return END_RESPONSE;
        }
        byte[] byteKey = iterator.key();
        String key = new String(byteKey);
        if (end != null && key.compareTo(end) >= 0) {
            processedAll = true;
            return END_RESPONSE;
        }
        byte[] value;
        try {
            Entity entity = Serializer.deserializeEntity(iterator.value());
            if (entity == null) {
                throw new RuntimeException("Failed to deserialize entity");
            }
            value = entity.data();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        stage = stage % 5 + 1;
        switch (stage) {
            case 1 -> {
                return String.format("%x\r\n", byteKey.length + 1 + value.length)
                    .getBytes(StandardCharsets.UTF_8);
            }
            case 2 -> {
                return byteKey;
            }
            case 3 -> {
                return SEPARATE;
            }
            case 4 -> {
                return value;
            }
            case 5 -> {
                iterator.next();
                return SEPARATE_RESPONSES;
            }
            default -> throw new IllegalStateException();
        }
    }
}
