package ok.dht.test.panov;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ServerImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerImpl.class);

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) {
        try {
            int port = 19234;
            int port2 = 19235;
            int port3 = 19236;
            String localhost = "http://localhost:";
            String url = localhost + port;
            String url2 = localhost + port2;
            String url3 = localhost + port3;
            Path path = Files.createTempDirectory("server");
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    List.of(url, url2, url3),
                    path
            );
            new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("Server boot error occurred: " + e.getMessage());
        }
    }
}
