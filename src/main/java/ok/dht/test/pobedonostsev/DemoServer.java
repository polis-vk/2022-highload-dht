package ok.dht.test.pobedonostsev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import ok.dht.ServiceConfig;

public final class DemoServer {

    private DemoServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("server")
        );
        DemoService service = new DemoService(cfg);
        service.start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
