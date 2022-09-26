package ok.dht.test.gerasimov.lsm;

import java.nio.file.Path;

public record Config(Path basePath, long flushThresholdBytes) {}
