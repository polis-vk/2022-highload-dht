package ok.dht.test.skroba;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;

public final class MyServer {
    private MyServer() {
        // Only main method
    }
    
    public static void main(String[] args) {
        try {
            int port = 3000;
            String url = "http://localhost:" + port;
            
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    Collections.singletonList(url),
                    Files.createTempDirectory("server")
            );
            
            new MyServiceImpl(cfg).start().get();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
