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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    private static final int IN_MEMORY_SIZE = 8388608;
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    private final ServiceConfig config;
    private HttpServerImpl server;
    private MemorySegmentDao memorySegmentDao;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        memorySegmentDao = new MemorySegmentDao(new Config(config.workingDir(), IN_MEMORY_SIZE));
        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
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
        Entry<MemorySegment> entry;
        try {
            entry = memorySegmentDao.get(MemorySegment.ofArray(id.toCharArray()));
        } catch (Exception e) {
            LOG.error("Error occurred while getting " + id + ' ' + e.getMessage());
            return new Response(
                    Response.INTERNAL_ERROR,
                    e.getMessage().getBytes(StandardCharsets.UTF_8)
            );
        }
        if (entry != null) {
            return new Response(
                    Response.OK,
                    entry.value().toByteArray()
            );
        }
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty() || request.getBody() == null) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }
        try {
            memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                    MemorySegment.ofArray(request.getBody())));
        } catch (Exception e) {
            LOG.error("Error occurred while inserting " + id + ' ' + e.getMessage());
            return new Response(
                    Response.INTERNAL_ERROR,
                    e.getMessage().getBytes(StandardCharsets.UTF_8)
            );
        }
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
        try {
            memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()), null));
        } catch (Exception e) {
            LOG.error("Error occurred while deleting " + id + ' ' + e.getMessage());
            return new Response(
                    Response.INTERNAL_ERROR,
                    e.getMessage().getBytes(StandardCharsets.UTF_8)
            );
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
