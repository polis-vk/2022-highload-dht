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
import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

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

    public Response handle(int method, String id, Request request, long timestamp) {
        switch (method) {
            case Request.METHOD_GET -> {
                return handleGet(id);
            }
            case Request.METHOD_PUT -> {
                return handlePut(id, request, timestamp);
            }
            case Request.METHOD_DELETE -> {
                return handleDelete(id, timestamp);
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
                Response response = emptyResponse(Response.NOT_FOUND);
                if (entry != null) {
                    response.addHeader("timestamp: " + entry.timestamp());
                }

                return response;
            }

            Response response = new Response(Response.OK, entry.value().toByteArray());
            response.addHeader("timestamp: " + entry.timestamp());

            return response;
        } catch (IOException ioException) {
            return emptyResponse(Response.BAD_REQUEST);
        }
    }

    public Response handlePut(
            String id,
            Request request,
            long timestamp
    ) {
        upsertById(id, MemorySegment.ofArray(request.getBody()), timestamp);

        return emptyResponse(Response.CREATED);
    }

    public Response handleDelete(
            String id,
            long timestamp
    ) {
        upsertById(id, null, timestamp);

        return emptyResponse(Response.ACCEPTED);
    }

    public ServiceConfig getConfig() {
        return config;
    }

    private static boolean isExistValue(Entry<MemorySegment> entry) {
        return entry != null && !entry.isTombstone();
    }

    private void upsertById(String id, MemorySegment segment, long timestamp) {
        BaseEntry<MemorySegment> entry = new BaseEntry<>(
                MemorySegment.ofArray(Utf8.toBytes(id)),
                segment,
                timestamp
        );

        memorySegmentDao.upsert(entry);
    }
}
