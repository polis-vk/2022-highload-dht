package ok.dht.test.ponomarev.rest.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Maps;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ponomarev.dao.MemorySegmentDao;
import ok.dht.test.ponomarev.rest.Server;
import ok.dht.test.ponomarev.rest.conf.ServerConfiguration;
import ok.dht.test.ponomarev.rest.handlers.EntityRequestHandler;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

public class DaoService implements Service {
    private final ServiceConfig config;

    private HttpServer server;
    private MemorySegmentDao dao;

    public DaoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            final Path workingDir = config.workingDir();
            if (Files.notExists(workingDir)) {
                Files.createDirectory(workingDir);
            }

            // Может создавать файлы
            dao = new MemorySegmentDao(workingDir, ServerConfiguration.DAO_INMEMORY_LIMIT_BYTES);

            server = new Server(
                createServerConfig(config.selfPort(), config.clusterUrls()),
                new EntityRequestHandler(dao)
            );
            server.start();

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        dao.close();
        server.stop();

        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createServerConfig(int port, Collection<String> clusterUrls) {
        final AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;

        final HttpServerConfig httpConfig = new HttpServerConfig();
        httpConfig.acceptors = new AcceptorConfig[]{
            acceptor
        };
        // При старте можно и побаловаться, почему бы нет?
        httpConfig.virtualHosts = createVirtualHosts(clusterUrls);

        return httpConfig;
    }

    private static Map<String, String[]> createVirtualHosts(Collection<String> clusterUrls) {
        if (clusterUrls == null || clusterUrls.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, String[]> virtualHosts = Maps.newHashMapWithExpectedSize(1);
        final String[] hosts = clusterUrls.stream()
            .map(url -> URI.create(url).getHost())
            .distinct()
            .toArray(String[]::new);

        virtualHosts.put(EntityRequestHandler.ROUTER_NAME, hosts);

        return virtualHosts;
    }
}
