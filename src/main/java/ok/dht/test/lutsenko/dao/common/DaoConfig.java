package ok.dht.test.lutsenko.dao.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DaoConfig(Path basePath, long flushThresholdBytes) {

    public static final int DEFAULT_FLUSH_THRESHOLD_BYTES = 1 << 20;

    public static DaoConfig defaultConfig() throws IOException {
        return new DaoConfig(
                Files.createTempDirectory("dao"),
                DEFAULT_FLUSH_THRESHOLD_BYTES
        );
    }

    public static DaoConfig defaultConfig(Path path) throws IOException {
        return new DaoConfig(Files.createDirectories(path), DEFAULT_FLUSH_THRESHOLD_BYTES);
    }
}
