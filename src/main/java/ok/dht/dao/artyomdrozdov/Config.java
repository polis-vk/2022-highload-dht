package ok.dht.dao.artyomdrozdov;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
