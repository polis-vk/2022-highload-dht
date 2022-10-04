package ok.dht.test.dergunov;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static ok.dht.test.dergunov.ServiceImpl.DEFAULT_FLUSH_THRESHOLD_BYTES;

public final class ServerTest {

    private ServerTest() {
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException,
            TimeoutException {
        int port = 8084;
        String url = "http://localhost:" + port;
        ServiceConfig config = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server3")
        );
        HttpServerImpl a = new HttpServerImpl(HttpServerImpl.createConfigFromPort(8084),
                config, DEFAULT_FLUSH_THRESHOLD_BYTES);
        a.start();
        //new ServiceImpl(config).start().get(1, TimeUnit.SECONDS);
    }
}
