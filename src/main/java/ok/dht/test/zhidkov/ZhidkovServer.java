package ok.dht.test.zhidkov;

import ok.dht.ServiceConfig;
import ok.dht.test.zhidkov.services.ZhidkovService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ZhidkovServer {

    private static final Logger LOG = LoggerFactory.getLogger(ZhidkovServer.class);

    private ZhidkovServer() {
        // Only main method
    }

    public static void main(String[] args) throws
            IOException,
            InterruptedException,
            ExecutionException,
            TimeoutException {
        int port = 8080;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new ZhidkovService(cfg).start().get(1, TimeUnit.SECONDS);
        LOG.info("Socket is ready: " + url);
    }
}
