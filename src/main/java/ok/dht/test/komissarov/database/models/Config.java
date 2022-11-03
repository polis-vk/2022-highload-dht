package ok.dht.test.komissarov.database.models;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
