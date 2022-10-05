package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerStart {
    private static final Logger logger = LoggerFactory.getLogger(ServerStart.class);

    private ServerStart() {

    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("/home/fedor/Documents/uni-data/highload/trash")
        );
        new HighLoadService(cfg).start().get(1, TimeUnit.SECONDS);
        logger.info("[SERVER START] Socket is ready: " + url);
    }
}
