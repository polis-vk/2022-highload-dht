package ok.dht.test.frolovm;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int port = 42342;
        String url = "http://localhost:42342" + port;
        Path path = Files.createTempDirectory("data");
        System.out.println(path);
        ServiceConfig cfg = new ServiceConfig(port, url, Collections.singletonList(url), path);
        ServiceImpl.Factory factory = new ServiceImpl.Factory();
        Service server = factory.create(cfg);
        server.start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
