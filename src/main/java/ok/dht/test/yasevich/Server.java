package ok.dht.test.yasevich;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class Server {

    private Server(ServiceConfig config) {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig config = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new ServiceImpl(config).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
