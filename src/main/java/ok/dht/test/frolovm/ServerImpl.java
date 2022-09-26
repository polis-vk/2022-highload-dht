package ok.dht.test.frolovm;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = 42342;
        String url = "http://localhost:42342" + port;
        Path path = Files.createTempDirectory("data");
        ServiceConfig cfg = new ServiceConfig(port, url, Collections.singletonList(url), path);
        ServiceImpl.Factory factory = new ServiceImpl.Factory();
        Service server = factory.create(cfg);
        server.start().get(1, TimeUnit.SECONDS);
    }
}
