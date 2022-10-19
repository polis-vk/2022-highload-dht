package ok.dht.test.kuleshov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.kuleshov.dao.BaseEntry;
import ok.dht.test.kuleshov.dao.Config;
import ok.dht.test.kuleshov.dao.Dao;
import ok.dht.test.kuleshov.dao.Entry;
import ok.dht.test.kuleshov.dao.storage.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.Request;
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
    private boolean isStarted;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Config daoConfig = new Config(config.workingDir(), DEFAULT_DAO_FLUSH_THRESHOLD);
        memorySegmentDao = new MemorySegmentDao(daoConfig);
        memorySegmentDao.flush();
        server = new CoolAsyncHttpServer(createConfigFromPort(config.selfPort()), this);
        isStarted = true;
        server.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (isStarted) {
            server.stop();
            memorySegmentDao.flush();
            memorySegmentDao.close();
            isStarted = false;
        }

        return CompletableFuture.completedFuture(null);
    }

    public Response handle(int method, String id, Request request) {
        switch (method) {
            case Request.METHOD_GET -> {
                return handleGet(id);
            }
            case Request.METHOD_PUT -> {
                return handlePut(id, request);
            }
            case Request.METHOD_DELETE -> {
                return handleDelete(id);
            }
            default -> {
                return emptyResponse(Response.BAD_REQUEST);
            }
        }
    }

    public Response handleGet(String id) {
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

    public Response handlePut(
            String id,
            Request request
    ) {
        upsertById(id, MemorySegment.ofArray(request.getBody()));

        return emptyResponse(Response.CREATED);
    }

    public Response handleDelete(
            String id
    ) {
        upsertById(id, null);

        return emptyResponse(Response.ACCEPTED);
    }

    public ServiceConfig getConfig() {
        return config;
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
