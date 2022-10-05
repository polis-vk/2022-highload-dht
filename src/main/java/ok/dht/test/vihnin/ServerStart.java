package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerStart {

    private ServerStart() {
        
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("/home/fedor/Documents/uni-data/highload/trash")
        );
        new HighLoadService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("[SERVER START] Socket is ready: " + url);
    }
}
