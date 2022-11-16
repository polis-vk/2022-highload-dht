package ok.dht.test.nadutkin.impl.replicas;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.nadutkin.database.BaseEntry;
import ok.dht.test.nadutkin.database.Config;
import ok.dht.test.nadutkin.database.Entry;
import ok.dht.test.nadutkin.database.impl.MemorySegmentDao;
import ok.dht.test.nadutkin.impl.parallel.HighLoadHttpServer;
import ok.dht.test.nadutkin.impl.range.ChunkResponse;
import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.utils.StoredValue;
import ok.dht.test.nadutkin.impl.utils.UtilsClass;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static ok.dht.test.nadutkin.impl.utils.UtilsClass.getBytes;
import static ok.dht.test.nadutkin.impl.utils.UtilsClass.getKey;

public class ReplicaService implements Service {
    protected final ServiceConfig config;
    protected HttpServer server;
    protected MemorySegmentDao dao;
    protected final int maximumPoolSize;

    protected final AtomicInteger storedData = new AtomicInteger(0);

    public ReplicaService(ServiceConfig config) {
        this.config = config;
        this.maximumPoolSize = Runtime.getRuntime().availableProcessors();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        long flushThresholdBytes = 1 << 18;
        this.dao = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        this.server = new HighLoadHttpServer(this.maximumPoolSize,
                UtilsClass.createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        this.server.stop();
        this.dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private Response upsert(MemorySegment key, @Nonnull byte[] body, String goodResponse) {
        MemorySegment value = MemorySegment.ofArray(body);
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
        return new Response(goodResponse, Response.EMPTY);
    }

    @Path(Constants.REPLICA_PATH)
    public Response handleV1(@Param(value = "id", required = true) String id,
                             Request request) {
        MemorySegment key = getKey(id);
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> value = dao.get(key);
                if (value == null) {
                    return new Response(Response.NOT_FOUND,
                            getBytes("Can't find any value, for id %1$s".formatted(id)));
                } else {
                    return new Response(Response.OK, value.value().toByteArray());
                }
            }
            case Request.METHOD_PUT -> {
                storedData.getAndIncrement();
                return upsert(key, request.getBody(), Response.CREATED);
            }
            case Request.METHOD_DELETE -> {
                return upsert(key, request.getBody(), Response.ACCEPTED);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED,
                        getBytes("Not implemented yet"));
            }
        }
    }

    @Path("/v0/entities")
    public void handleRange(@Param(value = "start") String start,
                            @Param(value = "end") String end,
                            Request request,
                            @Param(value = "session", required = true) HttpSession session) throws IOException {
        if (request.getMethod() != Request.METHOD_GET) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED,
                    getBytes("Not implemented yet")));
        }
        if (start == null || (end != null && start.compareTo(end) >= 0)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, getBytes("Start must be less than end")));
            return;
        }
        Response startResponse = new Response(Response.OK, Response.EMPTY);
        startResponse.getHeaders()[1] = "Transfer-Encoding: chunked";
        session.sendResponse(startResponse);

        MemorySegment startKey = getKey(start);
        MemorySegment endKey = end != null ? getKey(end) : null;

        Iterator<Entry<MemorySegment>> iterator = dao.get(startKey, endKey);

        ChunkResponse response = new ChunkResponse(Response.OK);

        while (iterator.hasNext()) {
            try {
                Entry<MemorySegment> entry = iterator.next();
                StoredValue value = UtilsClass.segmentToValue(entry.value().toByteArray());

                byte[] data = new ByteArrayBuilder()
                        .append(entry.key().toByteArray())
                        .append("\n")
                        .append(value.value())
                        .toBytes();

                if (!response.append(data)) {
                    session.sendResponse(response);
                    response = new ChunkResponse(Response.OK, data);
                }
            } catch (ClassNotFoundException e) {
                break;
            }

        }
        session.sendResponse(response);
        if (response.length() > 0) {
            session.sendResponse(new ChunkResponse(Response.OK, Response.EMPTY));
        }
    }
}
