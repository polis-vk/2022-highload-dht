package ok.dht.test.shik;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import ok.dht.ServiceConfig;

/**
 * Basic server stub.
 */
public final class Server {

    private static final Path DEFAULT_DATABASE_DIR = Paths.get("/var/folders/85/g8ft9y9d1kb9z88s8kgjh21m0000gp/T/server");
    private static final int DEFAULT_PORT = 19234;
    private static final String DEFAULT_URL = "http://localhost:" + DEFAULT_PORT;
    private static final ServiceConfig DEFAULT_CONFIG = new ServiceConfig(
        DEFAULT_PORT,
        DEFAULT_URL,
        Collections.singletonList(DEFAULT_URL),
        DEFAULT_DATABASE_DIR
    );

    private Server() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        new ServiceImpl(DEFAULT_CONFIG).start().get(10, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + DEFAULT_URL);
    }
}
