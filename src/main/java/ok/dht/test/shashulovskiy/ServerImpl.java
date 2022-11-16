package ok.dht.test.shashulovskiy;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(ServerImpl.class);

    private ServerImpl() {
        // Only main
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);

            String url = "http://localhost:" + port;
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    List.of("http://localhost:19234"),
                    Files.createTempDirectory("server" + port)
            );
            new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
            LOG.info("Socket is ready: " + url);
            LOG.info("Volumes mounted on " + cfg.workingDir());
            LOG.info("Volumes mounted on " + cfg.workingDir());
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Unable to start server: " + e.getMessage());
        }
    }
}
