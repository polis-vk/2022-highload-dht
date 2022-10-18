package ok.dht.test.ilin;

import ok.dht.ServiceConfig;
import ok.dht.test.ilin.service.EntityService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class IlinServer {
    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
            port,
            url,
            Collections.singletonList(url),
            Path.of("aboba")
        );
        new EntityService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
