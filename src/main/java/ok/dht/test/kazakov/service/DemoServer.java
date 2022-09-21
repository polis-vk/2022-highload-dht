package ok.dht.test.kazakov.service;

import ok.dht.ServiceConfig;

import java.nio.file.Path;
import java.util.Collections;

/**
 * Basic server stub.
 *
 * @author incubos
 */
public final class DemoServer {

    private DemoServer() {
        // Only main method
    }

    public static void main(final String[] args) throws Exception {
        final int port = 8080;
        final String url = "http://localhost:" + port;
        final ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("data", "server")
        );

        final DaoWebService daoWebService = new DaoWebService(cfg);
        try {
            daoWebService.start().get();
            Thread.sleep(Long.MAX_VALUE);
        } finally {
            daoWebService.stop().get();
        }
    }
}
