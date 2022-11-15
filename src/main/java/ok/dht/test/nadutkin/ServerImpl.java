package ok.dht.test.nadutkin;

import ok.dht.ServiceConfig;
import ok.dht.test.nadutkin.database.impl.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) throws IOException {
        int port = 19234;
        String url = "http://localhost:" + port;
        var temporary = Files.createTempDirectory("server");
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                temporary);
        ServiceImpl service = new ServiceImpl(cfg);
        try {
            service.start().get(1, TimeUnit.SECONDS);
            Constants.LOG.info("Socket is ready: {}", url);
        } catch (Exception e) {
            Constants.LOG.error("Service needs to be stopped. Exception: {}", e.getMessage());
            try {
                service.stop().get(1, TimeUnit.SECONDS);
            } catch (Exception stopException) {
                Constants.LOG.error("Service threw an exception, while stopping {}", stopException.getMessage());
            }
            try {
                // Java doesn't delete temporary directory
                if (Files.exists(temporary)) {
                    Files.delete(temporary);
                }
            } catch (IOException deletingException) {
                Constants.LOG.error("Unable to delete temporary dirrectory {}", deletingException.getMessage());
            }
        }
    }
}
