package ok.dht.test.dergunov.database;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
