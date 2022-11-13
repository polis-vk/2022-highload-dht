package ok.dht.test.yasevich.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    static final int ROUTED_REQUEST_TIMEOUT_MS = 100;
    static final Log LOGGER = LogFactory.getLog(ServiceImpl.class);
    static final String COORDINATOR_TIMESTAMP_HEADER = "timestamp-from-coordinator";
    static final int FLUSH_THRESHOLD = 1024 * 1024;
    static final int POOL_QUEUE_SIZE = 100;

    private final ServiceConfig serviceConfig;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.serviceConfig = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new CustomHttpServer(createConfigFromPort(serviceConfig.selfPort()), serviceConfig);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        return CompletableFuture.completedFuture(null);
    }

    static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (Exception e) {
            ServiceImpl.LOGGER.error("Error when sending " + response.getStatus());
            session.close();
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }

}
