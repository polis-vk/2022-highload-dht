package ok.dht.test.saskov;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class MyServer {

    private MyServer() {
        // Only main method
    }

    // With new folder
    public static void main(String[] args) throws Exception {
        int port = 12345;
        String url = "http://localhost:" + port;
        Path dir = Paths.get("/Users", "lev.saskov", "MyProgramms", "Polis", "database");
        // For tests
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory(dir, "server")
        );
        new MyService(cfg).start().get(1, TimeUnit.SECONDS);
    }
}
