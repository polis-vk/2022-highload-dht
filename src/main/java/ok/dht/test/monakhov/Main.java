package ok.dht.test.monakhov;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
            port,
            url,
            Collections.singletonList(url),
            Files.createTempDirectory("server")
        );
        new DbService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
