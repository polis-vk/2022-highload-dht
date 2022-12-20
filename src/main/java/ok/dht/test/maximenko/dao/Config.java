package ok.dht.test.maximenko.dao;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
