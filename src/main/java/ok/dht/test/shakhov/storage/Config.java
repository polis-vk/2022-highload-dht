package ok.dht.test.shakhov.storage;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
