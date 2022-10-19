package ok.dht.test.shakhov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shakhov.dao.BaseEntry;
import ok.dht.test.shakhov.dao.Dao;
import ok.dht.test.shakhov.dao.DaoConfig;
import ok.dht.test.shakhov.dao.Entry;
import ok.dht.test.shakhov.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class KeyValueService implements Service {
    private static final Logger log = LoggerFactory.getLogger(KeyValueService.class);

    private static final int FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024; // 4 mb

    private final ServiceConfig serviceConfig;
    private HttpServer server;
    private HttpClient httpClient;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    public KeyValueService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        DaoConfig daoConfig = new DaoConfig(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES);
        dao = new MemorySegmentDao(daoConfig);
        httpClient = HttpClient.newHttpClient();
        HttpServerConfig httpServerConfig = createHttpServerConfigFromPort(serviceConfig.selfPort());
        server = new KeyValueHttpServer(httpServerConfig, this::handleRequest);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private Response handleRequest(Request request, String id) {
        try {
            List<String> clusterUrls = serviceConfig.clusterUrls();
            String url = clusterUrls.get(getHashForKey(id) % clusterUrls.size());
            if (!serviceConfig.selfUrl().equals(url)) {
                return sendRequestToUrl(request, url);
            }

            return switch (request.getMethod()) {
                case Request.METHOD_GET -> handleGet(id);
                case Request.METHOD_PUT -> handlePut(id, request.getBody());
                case Request.METHOD_DELETE -> handleDelete(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (Exception e) {
            log.error("Unexpected error during processing {}", request, e);
            return internalError();
        }
    }

    private Response handleGet(String id) throws IOException {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        Entry<MemorySegment> entry = dao.get(key);
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    private Response handlePut(String id, byte[] body) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        MemorySegment value = MemorySegment.ofArray(body);
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response handleDelete(String id) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        Entry<MemorySegment> entry = new BaseEntry<>(key, null);
        dao.upsert(entry);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static HttpServerConfig createHttpServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] { acceptor };
        return httpConfig;
    }

    private int getHashForKey(String id) {
        return id.hashCode();
    }

    private Response sendRequestToUrl(Request request, String url) throws IOException, InterruptedException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                .method(request.getMethodName(),
                        HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                ).build();
        HttpResponse<byte[]> response = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
        return new Response(String.valueOf(response.statusCode()), response.body());
    }

    private static Response internalError() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    @ServiceFactory(stage = 3, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class StorageServiceFactory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new KeyValueService(config);
        }
    }
}
