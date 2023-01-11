package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerStart {
    private static final Logger logger = LoggerFactory.getLogger(ServerStart.class);
    private static final List<String> PORTS = List.of("19234", "19235");
    private static final String HOST = "http://localhost";

    private ServerStart() {

    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = Integer.parseInt(args[0]);
        String url = HOST + ":" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                PORTS.stream().map(p -> HOST + ":" + p).toList(),
                Path.of("/home/fedor/Documents/uni-data/highload/trash/" + port)
        );
        new HighLoadService(cfg).start().get(1, TimeUnit.SECONDS);
        logger.info("[SERVER START] Socket is ready: " + url);
    }
}
