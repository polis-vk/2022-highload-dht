package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.anikina.dao.BaseEntry;
import ok.dht.test.anikina.dao.Config;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.dao.MemorySegmentDao;
import ok.dht.test.anikina.utils.MemorySegmentUtils;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.nio.file.Path;

class DatabaseRequestHandler {
    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;

    private final MemorySegmentDao dao;

    DatabaseRequestHandler(Path workingDir) throws IOException {
        this.dao = new MemorySegmentDao(
                new Config(workingDir, FLUSH_THRESHOLD_BYTES));
    }

    void close() throws IOException {
        dao.close();
    }

    Response handle(String key, Request request) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                byte[] value = getFromDao(key);
                if (value == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, value);
            }
            case Request.METHOD_PUT -> {
                insertIntoDao(key, request.getBody());
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                insertIntoDao(key, null);
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private byte[] getFromDao(String key) {
        Entry<MemorySegment> entry = dao.get(MemorySegmentUtils.fromString(key));
        return entry == null ? null : MemorySegmentUtils.toBytes(entry.value());
    }

    private void insertIntoDao(String key, byte[] bytes) {
        dao.upsert(new BaseEntry<>(
                MemorySegmentUtils.fromString(key),
                MemorySegmentUtils.fromBytes(bytes))
        );
    }
}
