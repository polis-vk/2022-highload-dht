package ok.dht.test.skroba.dao.base;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}

