package ok.dht.test.armenakyan;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class DhtApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(Service.class);
    private static final int PORT = 54321;
    private static final String URL = "http://localhost:" + PORT;
    private static final String DB_PATH = "../";

    private DhtApplication() {
    }

    public static void main(String[] args) {

        try {
            ServiceConfig serviceConfig = new ServiceConfig(
                    PORT,
                    URL,
                    Collections.singletonList(URL),
                    Path.of(DB_PATH)
            );

            new DhtService(serviceConfig).start().get(1, TimeUnit.SECONDS);

            LOGGER.info("Server successfully started on port={}, url={}", PORT, URL);
            LOGGER.info("Database working dir: {}", serviceConfig.workingDir().toAbsolutePath().normalize());
        } catch (Exception e) {
            LOGGER.error("Couldn't start server: ", e);
        }
    }
}
