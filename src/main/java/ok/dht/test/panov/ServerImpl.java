package ok.dht.test.panov;

import ok.dht.ServiceConfig;
import ok.dht.test.drozdov.DemoService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        Path path = Files.createTempDirectory("server");
        System.out.println(path.toAbsolutePath().toString());
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                path
        );
        new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
