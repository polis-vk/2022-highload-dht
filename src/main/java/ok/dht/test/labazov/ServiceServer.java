package ok.dht.test.labazov;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class ServiceServer {

    private ServiceServer() {
        // Only main method
    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = switch (args[0]) {
            case "first" -> 19234;
             case "second" -> 12399;
             case "third" -> 13939;
            default -> throw new RuntimeException("Unknown port selection");
        };

        final String baseUrl = "http://localhost:";
        ServiceConfig cfg = new ServiceConfig(
                port,
                baseUrl + port,
                List.of(baseUrl + 19234),
                Files.createTempDirectory("highload-server")
        );
        new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
    }
}
