package ok.dht.test.skroba;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class MyServer {
    private static final Logger LOG = LoggerFactory.getLogger(
            MyServer.class
    );
    
    private MyServer() {
        // Only main method
    }
    
    public static void main(String[] args) {
        if (args.length != 1 || args[0] == null || !isRightInteger(args[0])) {
            LOG.error("Wrong args, arg must contain port");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        String url = "http://localhost:" + port;
        ServiceConfig cfg;
        
        try {
            cfg = new ServiceConfig(
                    port,
                    url,
                    List.of("http://localhost:3000", "http://localhost:3020", "http://localhost:3040"),
                    Files.createTempDirectory("server-" + port)
            );
        } catch (IOException e) {
            LOG.error("Can't create dir for db: " + e.getMessage());
            return;
        }
        
        try {
            new MyServiceImpl(cfg).start().get();
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }
    
    private static boolean isRightInteger(String s) {
        try {
            int result = Integer.parseInt(s);
            return result > 0 && result <= 9999;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
