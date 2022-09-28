package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import ok.dht.test.drozdov.DemoService;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerStart {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new HighLoadService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("[SERVER START] Socket is ready: " + url);
    }
}
