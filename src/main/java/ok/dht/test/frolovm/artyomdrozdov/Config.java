package ok.dht.test.frolovm.artyomdrozdov;

import java.nio.file.Path;

public record Config(
        Path basePath,
        long flushThresholdBytes) {
}
