package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.dao.base.Config;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DemoService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;
    private final long flushThreshold = 1 << 20; // 1 MB

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), flushThreshold));
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response;
                int requestMethod = request.getMethod();
                if (requestMethod == Request.METHOD_GET
                        || requestMethod == Request.METHOD_PUT
                        || requestMethod == Request.METHOD_DELETE) {
                    response = new Response(
                            Response.BAD_REQUEST,
                            Response.EMPTY
                    );
                } else {
                    response = new Response(
                            Response.METHOD_NOT_ALLOWED,
                            Response.EMPTY
                    );
                }
                session.sendResponse(response);
            }
        };
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
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

        BaseEntry<MemorySegment> entry = dao.get(fromString(id));

        if (entry == null) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }

        return new Response(
                Response.OK,
                entry.value().toByteArray()
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        }

        dao.upsert(new BaseEntry<>(
                fromString(id),
                MemorySegment.ofArray(request.getBody())
        ));

        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
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

        dao.upsert(new BaseEntry<>(
                fromString(id),
                null
        ));

        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
