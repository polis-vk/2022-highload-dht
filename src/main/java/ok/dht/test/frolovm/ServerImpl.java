package ok.dht.test.frolovm;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int port = Integer.parseInt(args[Integer.parseInt(args[3])]);
        String url = "http://localhost:" + port;
        Path path = Files.createTempDirectory("data" + args[3]);
        ServiceConfig cfg1 = new ServiceConfig(port, url, List.of("http://localhost:" + args[0], "http://localhost:" + args[1], "http://localhost:" + args[2]), path);
        ServiceImpl.Factory factory = new ServiceImpl.Factory();

        Service server = factory.create(cfg1);
        server.start().get(1, TimeUnit.SECONDS);
    }
}
