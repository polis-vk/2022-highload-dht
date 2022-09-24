package ok.dht.test.kurdyukov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kurdyukov.db.base.BaseEntry;
import ok.dht.test.kurdyukov.db.base.Config;
import ok.dht.test.kurdyukov.db.base.Dao;
import ok.dht.test.kurdyukov.db.base.Entry;
import ok.dht.test.kurdyukov.db.storage.MemorySegmentDao;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.server.ServerConfig;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private static final Logger logger = LoggerFactory.getLogger(ServiceImpl.class);

    private final ServiceConfig serviceConfig;

    private HttpServer httpServer;
    private Dao<MemorySegment, Entry<MemorySegment>> memorySegmentDao;


    public ServiceImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        httpServer = createHttpServer(
                serviceConfig.selfPort(),
                serviceConfig.selfUrl()
        );

        httpServer.start();
        httpServer.addRequestHandlers(this);

        memorySegmentDao = createDao(serviceConfig.workingDir());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        memorySegmentDao.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(
            @Param(value = "id", required = true) String id
    ) {
        if (id.isBlank()) {
            return responseEmpty(Response.BAD_REQUEST);
        }

        Entry<MemorySegment> entry;

        try {
            entry = memorySegmentDao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
        } catch (IOException e) {
            logger.error("Fail on get method with id: " + id, e);

            return responseEmpty(Response.INTERNAL_ERROR);
        }

        if (entry == null || entry.isTombstone()) {
            return responseEmpty(Response.NOT_FOUND);
        } else {
            return new Response(
                    Response.OK,
                    entry.value().toByteArray()
            );
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(
            @Param(value = "id", required = true) String id,
            Request request
    ) {
        if (id.isBlank()) {
            return responseEmpty(Response.BAD_REQUEST);
        }

        BaseEntry<MemorySegment> entry = new BaseEntry<>(
                MemorySegment.ofArray(Utf8.toBytes(id)),
                MemorySegment.ofArray(request.getBody())
        );

        return upsertWithHttpResponse(entry, Response.CREATED, id);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(
            @Param(value = "id", required = true) String id
    ) {
        if (id.isBlank()) {
            return responseEmpty(Response.BAD_REQUEST);
        }

        BaseEntry<MemorySegment> entry = new BaseEntry<>(
                MemorySegment.ofArray(Utf8.toBytes(id)),
                null
        );

        return upsertWithHttpResponse(entry, Response.ACCEPTED, id);
    }

    private Response upsertWithHttpResponse(
            BaseEntry<MemorySegment> entry,
            String status,
            String id
    ) {
        try {
            memorySegmentDao.upsert(entry);
            return responseEmpty(status);
        } catch (RuntimeException e) {
            logger.error("Error insert entry with key: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    private static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }

    private static Dao<MemorySegment, Entry<MemorySegment>> createDao(
            java.nio.file.Path workingDir
    ) throws IOException {
        Config configDao = new Config(
                workingDir,
                4 * 1024 // how RocksDB
        );

        try {
            return new MemorySegmentDao(configDao);
        } catch (IOException e) {
            logger.error("Error create database.");

            throw e;
        }
    }

    private static HttpServer createHttpServer(
            int port,
            String url
    ) throws IOException {
        return new HttpServer(
                httpServerConfig(
                        port,
                        url
                )
        ) {
            @Override
            public void handleDefault(
                    Request request,
                    HttpSession session
            ) throws IOException {
                session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    thread.selector.forEach(Session::close);
                }

                super.stop();
            }
        };
    }

    private static HttpServerConfig httpServerConfig(
            int port,
            String url
    ) {
        ServerConfig serverConfig = ServerConfig.from(url);

        HttpServerConfig httpConfig = new HttpServerConfig();

        httpConfig.acceptors = serverConfig.acceptors;

        for (var acceptor : httpConfig.acceptors) {
            acceptor.reusePort = true;
            acceptor.port = port;
        }

        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) throws IOException {
            return new ServiceImpl(config);
        }
    }
}