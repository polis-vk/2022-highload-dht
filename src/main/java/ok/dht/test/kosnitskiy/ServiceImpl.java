package ok.dht.test.kosnitskiy;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kosnitskiy.dao.BaseEntry;
import ok.dht.test.kosnitskiy.dao.Config;
import ok.dht.test.kosnitskiy.dao.Entry;
import ok.dht.test.kosnitskiy.dao.MemorySegmentDao;
import ok.dht.test.kosnitskiy.server.HttpServerImpl;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    private final ServiceConfig config;
    private HttpServerImpl server;
    private MemorySegmentDao memorySegmentDao;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        memorySegmentDao = new MemorySegmentDao(new Config(config.workingDir(), 8388608));
        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        memorySegmentDao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        Entry<MemorySegment> entry = memorySegmentDao.get(fromString(id));
        if (entry != null) {
            return new Response(
                    Response.OK,
                    toBytes(entry.value())
            );
        }
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        memorySegmentDao.upsert(new BaseEntry<>(fromString(id), fromBytes(request.getBody())));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        memorySegmentDao.upsert(new BaseEntry<>(fromString(id), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    public void fillTheDao() {
        Random random = new Random(1);
        for (int i = 0; i < 31500; i++) {
            byte[] value = new byte[102400];
            byte[] key = new byte[102400];
            random.nextBytes(value);
            random.nextBytes(key);
            memorySegmentDao.upsert(new BaseEntry<>(fromBytes(key), fromBytes(value)));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
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

    private byte[] toBytes(MemorySegment s) {
        return s == null ? null : s.toByteArray();
    }

    private MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }

    private MemorySegment fromBytes(byte[] bytes) {
        return bytes == null ? null : MemorySegment.ofArray(bytes);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
