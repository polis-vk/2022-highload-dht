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
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        ServiceImpl service = new ServiceImpl(cfg);
        try {
            service.start().get(1, TimeUnit.SECONDS);
            System.out.println("Socket is ready: " + url);
        } catch (Exception e) {
            service.stop();
            Constants.LOG.error("Service stopped. Exception: {}", e.getMessage());
        }
    }
}
