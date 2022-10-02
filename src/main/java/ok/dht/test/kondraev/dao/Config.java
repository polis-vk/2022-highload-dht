package ok.dht.test.kondraev.dao;

import java.nio.file.Path;

public final class Config {
    private final Path basePath;
    private final long flushThresholdBytes;

    public Config(
            Path basePath,
            long flushThresholdBytes) {
        this.basePath = basePath;
        this.flushThresholdBytes = flushThresholdBytes;
    }

    public Path basePath() {
        return basePath;
    }

    public long flushThresholdBytes() {
        return flushThresholdBytes;
    }
}
