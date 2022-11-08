package ok.dht.test.garanin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class DhtServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhtServer.class);

    private DhtServer() {
        // Only main method
    }

    public static void main(String[] args) {
        try {
            int port = 19234;
            String url = "http://localhost:" + port;
            var dir = Path.of(".");
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    Collections.singletonList(url),
                    dir
            );
            new DhtService(cfg).start().get(1, TimeUnit.SECONDS);
            LOGGER.info("Socket is ready: " + url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
