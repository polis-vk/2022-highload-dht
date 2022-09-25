package ok.dht.test.monakhov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.monakhov.database.BaseEntry;
import ok.dht.test.monakhov.database.Config;
import ok.dht.test.monakhov.database.Entry;
import ok.dht.test.monakhov.database.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MemorySegmentService implements Service {
    private static final int FLUSH_THRESHOLD_BYTES = 4 * 1024;
    private final ServiceConfig serviceConfig;
    private final Config daoConfig;
    private MemorySegmentDao dao;
    private HttpServer server;

    public MemorySegmentService(ServiceConfig serviceConfig, Config daoConfig) {
        this.serviceConfig = serviceConfig;
        this.daoConfig = daoConfig;
    }

    public static void main(String[] args) throws Exception {
        int port = 19234;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
            port,
            url,
            Collections.singletonList(url),
            Files.createTempDirectory("server")
        );
        Config daoConfig = createDaoConfig(cfg.workingDir());
        new MemorySegmentService(cfg, daoConfig).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    private static Config createDaoConfig(java.nio.file.Path basePath) {
        return new Config(basePath, FLUSH_THRESHOLD_BYTES);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new HttpServer(createConfigFromPort(serviceConfig.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }

            @Override
            public synchronized void stop() {
                Arrays.stream(selectors).forEach(thread ->
                    thread.selector.forEach(session ->
                        session.socket().close()
                    )
                );

                super.stop();
            }
        };
        dao = new MemorySegmentDao(daoConfig);
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response manageRequest(@Param(value = "id", required = true) String id, Request request) {
        if (id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> getEntity(id);
            case Request.METHOD_PUT -> putEntity(id, request);
            case Request.METHOD_DELETE -> deleteEntity(id);
            default -> new Response(Response.METHOD_NOT_ALLOWED);
        };
    }

    public Response getEntity(String id) {
        Entry<MemorySegment> entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
        if (entry == null || entry.isTombstone()) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    public Response putEntity(String id, Request request) {
        dao.upsert(new BaseEntry<>(
            MemorySegment.ofArray(Utf8.toBytes(id)),
            MemorySegment.ofArray(request.getBody())
        ));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(String id) {
        dao.upsert(new BaseEntry<>(
            MemorySegment.ofArray(Utf8.toBytes(id)),
            null
        ));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new MemorySegmentService(config, createDaoConfig(config.workingDir()));
        }
    }
}
