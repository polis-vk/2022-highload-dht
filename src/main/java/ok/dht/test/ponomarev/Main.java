package ok.dht.test.ponomarev;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ponomarev.rest.service.DaoService;

public class Main {
    private static final int PORT = 8080;
    private static final String URL = "http://localhost";

    public static void main(String[] args) throws Exception {
        final String url = URL + ':' + PORT;

        final ServiceConfig cfg = new ServiceConfig(
            PORT,
            url,
            Collections.singletonList(url),
            Files.createTempDirectory("server")
        );

        final Service service = new DaoService(cfg);
        service.start().get(1, TimeUnit.SECONDS);
    }
}
