package ok.dht.test.skroba;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Collections;

public final class MyServer {
    private static final Logger LOG = LoggerFactory.getLogger(
            MyServer.class
    );
    
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
            LOG.error(e.getMessage());
        }
    }
}
