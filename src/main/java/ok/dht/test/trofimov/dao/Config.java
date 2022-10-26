package ok.dht.test.trofimov.dao;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
