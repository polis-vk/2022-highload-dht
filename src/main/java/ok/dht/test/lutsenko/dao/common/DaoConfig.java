package ok.dht.test.lutsenko.dao.common;

import java.nio.file.Path;

public record DaoConfig(
        Path basePath,
        long flushThresholdBytes) {
}
