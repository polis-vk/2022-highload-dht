package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.anikina.dao.BaseEntry;
import ok.dht.test.anikina.dao.Config;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.dao.MemorySegmentDao;
import ok.dht.test.anikina.utils.Utils;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

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

    Response handle(int method, String key, byte[] body, byte[] timestamp) {
        switch (method) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry = dao.get(Utils.memorySegmentFromString(key));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                if (entry.value() == null) {
                    return new Response(Response.NOT_FOUND, entry.timestamp().toByteArray());
                }
                byte[] value = entry.value().toByteArray();
                byte[] responseBody = Utils.toByteArray(entry.timestamp().toByteArray(), value);
                return new Response(Response.OK, responseBody);
            }
            case Request.METHOD_PUT -> {
                insertIntoDao(key, body, timestamp);
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                insertIntoDao(key, null, timestamp);
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> throw new IllegalArgumentException("Request method not supported.");
        }
    }

    Iterator<Entry<MemorySegment>> getIterator(String start, String end) {
        return dao.get(Utils.memorySegmentFromString(start), Utils.memorySegmentFromString(end));
    }

    private void insertIntoDao(String key, byte[] bytes, byte[] timestamp) {
        dao.upsert(
                new BaseEntry<>(
                        Utils.memorySegmentFromString(key),
                        Utils.memorySegmentFromBytes(bytes),
                        MemorySegment.ofArray(timestamp)
                )
        );
    }
}
