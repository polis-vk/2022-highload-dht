package ok.dht.test.monakhov.database;

import java.nio.file.Path;

public record Config(Path basePath, long flushThresholdBytes) {}
