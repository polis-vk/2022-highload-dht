package ok.dht.test.maximenko;


import ok.dht.ServiceConfig;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class Server {
    private Server() {

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
        new DemoService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Server is listening on port: " + port);
    }
}
