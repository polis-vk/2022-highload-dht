package ok.dht.test.garanin;

import ok.dht.ServiceConfig;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DhtServer {
    private DhtServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        var dir = Path.of(".");
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                dir
        );
        new DhtService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
        System.out.println(dir.toAbsolutePath().normalize());
    }
}
