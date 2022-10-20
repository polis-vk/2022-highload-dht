package ok.dht.test.kondraev;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kondraev.dao.Dao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.os.SchedulingPolicy;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Service implements ok.dht.Service {
    private static final long FLUSH_THRESHOLD_BYTES = 1 << 20; // 1 MB
    private static final Logger LOG = LoggerFactory.getLogger(Service.class);
    private final ServiceConfig config;
    private HttpServer server;
    private Dao dao;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> start() throws IOException {
        try {
            Files.createDirectory(config.workingDir());
        } catch (FileAlreadyExistsException ignored) {
            // directory exists, nothing to do
        }
        dao = Dao.of(FLUSH_THRESHOLD_BYTES, config.workingDir());
        server = new Server(createConfig(config.selfPort()), dao);
        return CompletableFuture.runAsync(server::start);
    }

    @Override
    public CompletableFuture<Void> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.affinity = true; // assign each thread a processor
        httpConfig.schedulingPolicy = SchedulingPolicy.BATCH;
        httpConfig.minWorkers = 4;
        httpConfig.maxWorkers = 8;
        httpConfig.queueTime = 900; // ms, max time for one request
        return httpConfig;
    }

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        String defaultUrl = "http://localhost:19234";
        if (args.length != 1) {
            LOG.error("Usage: ./gradlew run --args={}", defaultUrl);
            return;
        }
        String url = args[0];
        int port;
        try {
            port = Integer.parseInt(url.split(":(?=\\d+$)", 2)[1]);
        } catch (IndexOutOfBoundsException ignored) {
            LOG.error("Url in wrong format. Try \"{}\"", defaultUrl);
            return;
        }
        new Service(new ServiceConfig(
                port,
                url,
                List.of(url),
                Files.createTempDirectory("kondraev-server")
        )).start().get(1, TimeUnit.SECONDS);
        LOG.info("Ready on {}", url);
    }

    @ServiceFactory(stage = 2, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new Service(config);
        }
    }
}
