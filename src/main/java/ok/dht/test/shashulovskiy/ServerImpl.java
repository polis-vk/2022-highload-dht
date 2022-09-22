package ok.dht.test.shashulovskiy;

import ok.dht.ServiceConfig;
import ok.dht.test.drozdov.DemoService;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ServerImpl {

    private ServerImpl() {
        // Only main
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
        new ServiceImpl(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
        System.out.println("Volumes mounted on " + cfg.workingDir());
    }
}
