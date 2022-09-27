package ok.dht.test.panov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.panov.dao.BaseEntry;
import ok.dht.test.panov.dao.Config;
import ok.dht.test.panov.dao.Entry;
import ok.dht.test.panov.dao.lsm.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

public class ServiceImpl implements Service {

    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;

    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = initHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServer initHttpServer(final HttpServerConfig config) throws IOException {
        return new HttpServer(config) {

            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            }

            @Override
            public synchronized void stop() {
                cleanup.shutdown();

                Arrays.stream(selectors)
                        .flatMap(selectorThread -> StreamSupport.stream(selectorThread.selector.spliterator(), false))
                        .forEach(Session::close);

                super.stop();
            }
        };
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();

        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Path("/v0/entity")
    public Response handleEntity(final Request request, @Param(value = "id", required = true) final String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, "Id is empty".getBytes(StandardCharsets.UTF_8));
        }

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> getEntity(id);
            case Request.METHOD_PUT -> putEntity(request, id);
            case Request.METHOD_DELETE -> deleteEntity(id);
            default -> new Response(Response.BAD_REQUEST, "Unhandled method".getBytes(StandardCharsets.UTF_8));
        };
    }

    private Response getEntity(final String id) {
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> value = dao.get(key);

        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, value.value().toByteArray());
    }

    public Response putEntity(final Request request, final String id) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));
        MemorySegment value = MemorySegment.ofByteBuffer(ByteBuffer.wrap(request.getBody()));

        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(final String id) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));

        Entry<MemorySegment> entry = new BaseEntry<>(key, null);
        dao.upsert(entry);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
