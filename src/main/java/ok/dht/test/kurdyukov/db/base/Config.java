package ok.dht.test.kurdyukov.db.base;

import java.nio.file.Path;

public record Config(Path basePath, long flushThresholdBytes) {}