package ok.dht.test.lutsenko;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;

public class DemoServer {

    private DemoServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig serviceConfig = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        new DemoService(serviceConfig).start();
        System.out.println("Socket is ready: " + url);
    }
}
