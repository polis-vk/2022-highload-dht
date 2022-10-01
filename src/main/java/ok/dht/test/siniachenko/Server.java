package ok.dht.test.siniachenko;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.service.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = 12345;
        String url = String.format("http://localhost:%d", port);
        ServiceConfig config;
        try {
            config = new ServiceConfig(
                    port,
                    url,
                    Collections.singletonList(url),
                    Files.createTempDirectory("server")
            );
        } catch (IOException e) {
            System.err.println("Cannot create server directory \"server\"");
            throw e;
        }
        new Service(config).start().get(1, TimeUnit.SECONDS);
        System.out.printf("Started Server on %s%n", url);
    }
}
