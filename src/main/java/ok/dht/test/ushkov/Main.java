package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8000;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        Service service = new ServiceImpl(cfg);
        service.start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
