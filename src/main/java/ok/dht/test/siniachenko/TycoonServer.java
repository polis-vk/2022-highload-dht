package ok.dht.test.siniachenko;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.service.TycoonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TycoonServer {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonServer.class);

    private TycoonServer() {
    }

    public static void main(String[] args)
        throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 12345;
        String url = String.format("http://localhost:%d", port);
        ServiceConfig config;
        try {
            config = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
            );
        } catch (IOException e) {
            LOG.error("Cannot create server directory \"server\"");
            throw e;
        }
        new TycoonService(config).start().get(1, TimeUnit.SECONDS);
        LOG.info("Started Server on {}", url);
    }
}
