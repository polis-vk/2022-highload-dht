package ok.dht.test.pashchenko;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class DemoServer {

    private DemoServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int[] ports = new int[3];
        List<String> cluster = new ArrayList<>(ports.length);
        for (int i = 0; i < ports.length; i++) {
            ports[i] = i + 12353;
            cluster.add("http://localhost:" + ports[i]);
        }

        for (int i = 0; i < ports.length; i++) {
            String url = cluster.get(i);
            ServiceConfig cfg = new ServiceConfig(
                    ports[i],
                    url,
                    Collections.singletonList(url),
                    Files.createTempDirectory("server")
            );
            new DemoService(cfg).start().get(1, TimeUnit.SECONDS);
            System.out.println("Socket is ready: " + url);
        }
    }
}
