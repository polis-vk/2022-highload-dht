package ok.dht.test.dergunov;

import ok.dht.ServiceConfig;
import ok.dht.test.drozdov.DemoService;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerTest {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 8081;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new DemoService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
