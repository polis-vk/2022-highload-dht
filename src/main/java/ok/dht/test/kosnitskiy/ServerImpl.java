package ok.dht.test.kosnitskiy;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(ServerImpl.class);

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) throws IOException,
            ExecutionException,
            InterruptedException,
            TimeoutException {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Path.of("/home/sanerin/GitHub/data_files")
        );
        ServiceImpl service = new ServiceImpl(cfg);
        service.start().get(1, TimeUnit.SECONDS);
        LOG.info("Socket is ready: " + url);
    }
}
