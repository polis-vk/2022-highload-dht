package ok.dht.test.shestakova.dao.baseElements;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
