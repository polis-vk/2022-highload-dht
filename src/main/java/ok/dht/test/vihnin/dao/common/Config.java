package ok.dht.test.vihnin.dao.common;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
