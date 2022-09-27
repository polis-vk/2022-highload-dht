package ok.dht.test.siniachenko.storage;

import java.nio.file.Path;

public record Config(
    Path basePath,
    long flushThresholdBytes) {
}
