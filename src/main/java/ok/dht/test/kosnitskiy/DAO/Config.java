package ok.dht.test.kosnitskiy.DAO;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
