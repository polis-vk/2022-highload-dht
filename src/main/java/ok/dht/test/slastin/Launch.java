package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class Launch {
    private static final Logger log = LoggerFactory.getLogger(Launch.class);

    private static final int DEFAULT_SERVER_PORT = 2022;
    private static final String DEFAULT_SERVER_URL = "http://localhost:" + DEFAULT_SERVER_PORT;
    private static final String DEFAULT_SERVER_NAME = "server";

    private Launch() {
        // Only main method
    }

    private static Path createServerDirectory(final String serverDirectoryName) throws IOException {
        final Path serverDirectory = Path.of(serverDirectoryName);
        if (Files.notExists(serverDirectory)) {
            Files.createDirectories(serverDirectory);
        }
        return serverDirectory;
    }

    public static void main(String[] args) {
        try {
            String serverDirectoryName = args.length == 0 ? null : args[0];

            Path serverDirectory = serverDirectoryName == null
                    ? Files.createTempDirectory(DEFAULT_SERVER_NAME)
                    : createServerDirectory(serverDirectoryName);

            ServiceConfig cfg = new ServiceConfig(
                    DEFAULT_SERVER_PORT,
                    DEFAULT_SERVER_URL,
                    Collections.singletonList(DEFAULT_SERVER_URL),
                    serverDirectory
            );
            new SladkiiService(cfg).start().get(1, TimeUnit.SECONDS);

            log.info("Server is located by {}", DEFAULT_SERVER_URL);
        } catch (Exception e) {
            log.error("Error occurred with server", e);
        }
    }
}
