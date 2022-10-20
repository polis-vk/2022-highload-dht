package ok.dht.test.saskov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.saskov.database.BaseEntry;
import ok.dht.test.saskov.database.Config;
import ok.dht.test.saskov.database.Dao;
import ok.dht.test.saskov.database.Entry;
import ok.dht.test.saskov.database.drozdov.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MyService implements Service {
    private static final long FLUSH_THRESHOLD = 1 << 20; // 1 MB
    private static final Logger log = LoggerFactory.getLogger(MyService.class);
    private final ServiceConfig config;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private HttpServer server;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(
                new Config(config.workingDir(), FLUSH_THRESHOLD)
        );
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }
        };
        server.addRequestHandlers(this);
        server.start();

        log.info("Service was started on {} successfully.", config.selfUrl());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        log.info("Service was stopped successfully.");
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id=") String key) {
        if (isBadKey(key)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        Entry<MemorySegment> entry;
        try {
            entry = dao.get(getSegmentFromString(key));
        } catch (Exception e) {
            return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(e.toString()));
        }

        return entry == null || entry.isTombstone()
                ? new Response(Response.NOT_FOUND, Response.EMPTY) :
                new Response(Response.OK, entry.value().toByteArray());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param("id=") String key, Request request) {
        if (isBadKey(key)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        try {
            dao.upsert(
                    new BaseEntry<>(getSegmentFromString(key), MemorySegment.ofArray(request.getBody()))
            );
        } catch (Exception e) {
            return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(e.toString()));
        }

        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param("id=") String key) {
        if (isBadKey(key)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        try {
            dao.upsert(
                    new BaseEntry<>(getSegmentFromString(key), null)
            );
        } catch (Exception e) {
            return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(e.toString()));
        }

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

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }

    private static MemorySegment getSegmentFromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }

    private static boolean isBadKey(String key) {
        return key == null || key.isEmpty();
    }
}
