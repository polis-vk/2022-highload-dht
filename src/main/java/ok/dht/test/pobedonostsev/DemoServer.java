package ok.dht.test.pobedonostsev;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DemoServer {

    private static final Logger LOG = LoggerFactory.getLogger(DemoServer.class);

    private DemoServer() {
        // Only main method
    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int basePort = 19234;
        List<String> urls = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            urls.add("http://localhost:" + (basePort + i));
        }
        for (int i = 0; i < 3; i++) {
            String url = urls.get(i);
            ServiceConfig cfg = new ServiceConfig(
                    basePort + i,
                    url,
                    urls,
                    Path.of("server" + i)
            );
            DemoService service = new DemoService(cfg);
            service.start().get(1, TimeUnit.SECONDS);
            LOG.trace("Socket is ready: " + url);
        }
    }
}
