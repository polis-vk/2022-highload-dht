package ok.dht.test.garanin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class DhtServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhtServer.class);

    private DhtServer() {
        // Only main method
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            String url = args[2];
            var dir = Path.of(args[1]);
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    new ArrayList<>(Arrays.asList(args).subList(2, args.length)).stream().sorted().toList(),
                    dir
            );
            new DhtService(cfg).start().get(1, TimeUnit.SECONDS);
            LOGGER.info("Socket is ready: " + url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

//./gradlew run --args="5001 ./clusterDbs/1/ http://localhost:5001 http://localhost:5002 http://localhost:5003"
//./gradlew run --args="5002 ./clusterDbs/2/ http://localhost:5002 http://localhost:5001 http://localhost:5003"
//./gradlew run --args="5003 ./clusterDbs/3/ http://localhost:5003 http://localhost:5002 http://localhost:5001"
