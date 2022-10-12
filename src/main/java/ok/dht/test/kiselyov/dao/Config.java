package ok.dht.test.kiselyov.dao;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
