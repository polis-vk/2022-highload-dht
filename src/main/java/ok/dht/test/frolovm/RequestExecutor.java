package ok.dht.test.frolovm;

import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;

public class RequestExecutor {

    private static final int SIZE_LONG = Long.SIZE / Long.BYTES;

    private final DB dao;

    public RequestExecutor(DB dao) {
        this.dao = dao;
    }

    private static byte[] getData(byte[] res, ByteBuffer buffer) {
        byte[] resultData = new byte[res.length - SIZE_LONG - 1];
        buffer.get(SIZE_LONG + 1, resultData);
        return resultData;
    }

    public Response entityHandlerSelf(String id, Request request, long timestamp) {
        if (!Utils.checkId(id)) {
            return new Response(Response.BAD_REQUEST, Utf8.toBytes(Utils.BAD_ID));
        }

        return switch (request.getMethod()) {
            case Request.METHOD_PUT -> putHandler(request, id, timestamp);
            case Request.METHOD_GET -> getHandler(id);
            case Request.METHOD_DELETE -> deleteHandler(id, timestamp);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(Utils.NO_SUCH_METHOD));
        };
    }

    public Iterator<Pair<byte[], byte[]>> entityHandlerRange(String start, String end) {
        final byte[] startBytes = Utils.stringToByte(start);
        final byte[] endBytes = Utils.checkId(end) ? Utils.stringToByte(end) : null;
        final DBIterator it = dao.iterator();
        it.seek(startBytes);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext() && Utils.compareArrays(it.peekNext().getKey(), endBytes) < 0;
            }

            @Override
            public Pair<byte[], byte[]> next() {
                Map.Entry<byte[], byte[]> entry = it.next();
                ByteBuffer buffer = ByteBuffer.allocate(entry.getValue().length);
                buffer.put(entry.getValue());
                buffer.flip();
                return new Pair<>(entry.getKey(), getData(entry.getValue(), buffer));
            }
        };
    }

    private Response putHandler(Request request, String id, long timestamp) {
        dao.put(Utils.stringToByte(id), Utils.dataToBytes(timestamp, request.getBody()));
        return Utils.emptyResponse(Response.CREATED);
    }

    private Response deleteHandler(String id, long timestamp) {
        dao.put(Utils.stringToByte(id), Utils.dataToBytes(timestamp, null));
        return Utils.emptyResponse(Response.ACCEPTED);
    }

    private Response getHandler(String id) {
        byte[] res = dao.get(Utils.stringToByte(id));

        if (res == null) {
            Response response = Utils.emptyResponse(Response.NOT_FOUND);
            response.addHeader(Utils.TIMESTAMP_ONE_NIO + System.currentTimeMillis());
            return response;
        }

        ByteBuffer buffer = ByteBuffer.allocate(res.length);
        buffer.put(res);
        buffer.flip();
        long timestamp = buffer.getLong();

        if (res[SIZE_LONG] == 1) {
            Response response = Utils.emptyResponse(Response.NOT_FOUND);
            response.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);
            response.addHeader(Utils.TOMBSTONE_ONE_NIO);
            return response;
        } else {
            Response response = new Response(Response.OK, getData(res, buffer));
            response.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);
            return response;
        }
    }
}
