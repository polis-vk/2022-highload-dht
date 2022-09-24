package ok.dht.test.shashulovskiy;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private ServerImpl() {
        // Only main
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
            System.out.println("Socket is ready: " + url);
            System.out.println("Volumes mounted on " + cfg.workingDir());
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("Unable to start server: " + e.getMessage());
        }
    }
}
