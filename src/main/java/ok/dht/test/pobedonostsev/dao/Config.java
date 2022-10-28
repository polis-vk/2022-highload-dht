package ok.dht.test.pobedonostsev.dao;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
