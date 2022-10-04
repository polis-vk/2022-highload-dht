package ok.dht.test.lutsenko.dao.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DaoConfig(Path basePath, long flushThresholdBytes) {

    public static DaoConfig defaultConfig() throws IOException {
        return new DaoConfig(
                Files.createTempDirectory("dao"),
                1 << 20
        );
    }
}
