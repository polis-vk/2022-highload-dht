package ok.dht.test.kuleshov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.kuleshov.dao.BaseEntry;
import ok.dht.test.kuleshov.dao.Config;
import ok.dht.test.kuleshov.dao.Dao;
import ok.dht.test.kuleshov.dao.Entry;
import ok.dht.test.kuleshov.dao.storage.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.util.Utf8;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.kuleshov.Validator.isCorrectId;
import static ok.dht.test.kuleshov.utils.ConfigUtils.createConfigFromPort;

public class Service implements ok.dht.Service {
    private final ServiceConfig config;
    private static final int DEFAULT_DAO_FLUSH_THRESHOLD = 8192;
    private Dao<MemorySegment, Entry<MemorySegment>> memorySegmentDao;
    private HttpServer server;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new CoolAsyncHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        Config daoConfig = new Config(config.workingDir(), DEFAULT_DAO_FLUSH_THRESHOLD);
        memorySegmentDao = new MemorySegmentDao(daoConfig);
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
    public Response handleGet(
            @Param(value = "id", required = true) String id
    ) {
        if (!isCorrectId(id)) {
            return emptyResponse(Response.BAD_REQUEST);
        }

        try {
            Entry<MemorySegment> entry = memorySegmentDao.get(MemorySegment.ofArray(Utf8.toBytes(id)));

            if (!isExistValue(entry)) {
                return emptyResponse(Response.NOT_FOUND);
            }

            return new Response(Response.OK, entry.value().toByteArray());
        } catch (IOException ioException) {
            return emptyResponse(Response.BAD_REQUEST);
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(
            @Param(value = "id", required = true) String id,
            Request request
    ) {
        if (!isCorrectId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        upsertById(id, MemorySegment.ofArray(request.getBody()));

        return emptyResponse(Response.CREATED);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(
            @Param(value = "id", required = true) String id
    ) {
        if (!isCorrectId(id)) {
            return emptyResponse(Response.BAD_REQUEST);
        }

        upsertById(id, null);

        return emptyResponse(Response.ACCEPTED);
    }

    private static boolean isExistValue(Entry<MemorySegment> entry) {
        return entry != null && !entry.isTombstone();
    }

    private static Response emptyResponse(String statusCode) {
        return new Response(statusCode, Response.EMPTY);
    }

    private void upsertById(String id, MemorySegment segment) {
        BaseEntry<MemorySegment> entry = new BaseEntry<>(
                MemorySegment.ofArray(Utf8.toBytes(id)),
                segment
        );

        memorySegmentDao.upsert(entry);
    }
}
