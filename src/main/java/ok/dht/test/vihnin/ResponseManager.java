package ok.dht.test.vihnin;

import ok.dht.test.vihnin.database.DataBase;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;

import static ok.dht.test.vihnin.ParallelHttpServer.TIME_HEADER_NAME;
import static ok.dht.test.vihnin.ServiceUtils.ENDPOINT;
import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;

public class ResponseManager {

    private static final byte TOMBSTONE = (byte) 0xFF;
    private final DataBase<String, byte[]> storage;

    public ResponseManager(DataBase<String, byte[]> storage) {
        this.storage = storage;
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        try {
            var searchResult = storage.get(id);

            long timestamp = parseTimeFromData(searchResult);
            byte status = parseStatusFromData(searchResult);
            byte[] data = parseActualDataFromData(searchResult);

            Response response = new Response(
                    status == TOMBSTONE
                            ? Response.NOT_FOUND
                            : Response.OK,
                    data
            );

            response.addHeader(TIME_HEADER_NAME + ": " + timestamp);
            return response;
        } catch (RuntimeException e) {
            return emptyResponse(Response.NOT_FOUND);
        }
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        var requestBody = request.getBody();

        storage.put(id, convertData(requestBody, getTimestamp(request)));

        return emptyResponse(Response.CREATED);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id, Request request) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        storage.put(id, tombstone(getTimestamp(request)));

        return emptyResponse(Response.ACCEPTED);
    }

    public Response handleRequest(Request request) {
        String id = request.getParameter("id=");

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(id);
            case Request.METHOD_PUT -> handlePut(id, request);
            case Request.METHOD_DELETE -> handleDelete(id, request);
            default -> null;
        };

    }

    private static long getTimestamp(Request request) {
        return Long.parseLong(request.getHeader(TIME_HEADER_NAME).substring(2));
    }

    private static byte[] tombstone(long timestamp) {
        byte[] data = convertData(new byte[0], timestamp);
        data[0] = TOMBSTONE;
        return data;
    }

    private static byte[] convertData(byte[] data, long timestamp) {
        byte[] timeBytes = new byte[9];
        // 1 + 8
        long timestampCope = timestamp;
        for (int i = 0; i < 8; i++) {
            timeBytes[8 - i] = (byte) timestampCope;
            timestampCope >>= 8;
        }
        byte[] actualBytes = new byte[data.length + timeBytes.length];
        System.arraycopy(timeBytes, 0, actualBytes, 0, timeBytes.length);
        System.arraycopy(data, 0, actualBytes, timeBytes.length, data.length);
        return actualBytes;
    }

    private static long parseTimeFromData(byte[] data) {
        long timestamp = 0;
        for (int i = 0; i < 8; i++) {
            timestamp <<= 8;
            timestamp += data[i + 1];
        }
        return timestamp;
    }

    private static byte parseStatusFromData(byte[] data) {
        return data[0];
    }

    private static byte[] parseActualDataFromData(byte[] data) {
        byte[] actualData = new byte[data.length - 9];
        System.arraycopy(data, 9, actualData, 0, data.length - 9);
        return actualData;
    }
}
