package ok.dht.test.shakhov.dao;

import java.nio.file.Path;

public record DaoConfig(
        Path basePath,
        long flushThresholdBytes) {
}
