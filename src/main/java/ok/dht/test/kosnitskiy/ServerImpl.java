package ok.dht.test.kosnitskiy;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(ServerImpl.class);

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) {
        try {
            int port = 19234;
            String url = "http://localhost:" + port;
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    Collections.singletonList(url),
                    Files.createTempDirectory("server")
            );
            new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
            LOG.info("Socket is ready: " + url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
