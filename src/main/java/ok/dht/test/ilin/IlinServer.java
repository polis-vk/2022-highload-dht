package ok.dht.test.ilin;

import ok.dht.ServiceConfig;
import ok.dht.test.ilin.service.EntityService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IlinServer {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        String url = "http://localhost:" + port;
        System.out.println("Socket is starting: " + url);
        ServiceConfig cfg = new ServiceConfig(
            port,
            url,
            List.of(url, args[1], args[2]),
            Path.of("aboba" + args[0])
        );
        new EntityService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
