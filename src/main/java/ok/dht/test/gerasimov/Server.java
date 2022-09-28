package ok.dht.test.gerasimov;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Server {
    private static final int PORT = 8000;
    private static final String URL = "http://localhost:";

    private Server() {
    }

    public static void main(String[] args) throws IOException {
        ServiceConfig serviceConfig = null;
        serviceConfig = new ServiceConfig(
                PORT,
                URL,
                Collections.singletonList(URL),
                Files.createTempDirectory("server")
        );

        ServiceImpl service = new ServiceImpl(serviceConfig);
        try {
            service.start().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            service.stop();
        }
    }
}
