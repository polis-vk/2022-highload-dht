package ok.dht.test.kosnitskiy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import ok.dht.ServiceConfig;

public final class ServerImpl {

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
            System.out.println("Socket is ready: " + url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
