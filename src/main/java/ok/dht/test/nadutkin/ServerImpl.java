package ok.dht.test.nadutkin;

import ok.dht.ServiceConfig;
import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.ServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
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
        if (args == null || args.length != 1) {
            throw new IllegalArgumentException("You need to pass port to args");
        }
        int port = Integer.parseInt(args[0]);
        String url = "http://localhost:" + port;
        var temporary = Files.createTempDirectory("server" + port);
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                List.of("http://localhost:19234",
                        "http://localhost:19235",
                        "http://localhost:19236"),
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
