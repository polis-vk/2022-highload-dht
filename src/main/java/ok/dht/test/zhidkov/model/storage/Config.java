package ok.dht.test.zhidkov.model.storage;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
