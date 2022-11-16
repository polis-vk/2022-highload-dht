package ok.dht.test.vihnin.utils;

import ok.dht.test.vihnin.ResponseAccumulator;
import ok.dht.test.vihnin.ResponseManager;
import ok.dht.test.vihnin.database.Row;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.Optional;

import static ok.dht.test.vihnin.ParallelHttpServer.TIME_HEADER_NAME;
import static ok.dht.test.vihnin.ParallelHttpServer.handleException;
import static ok.dht.test.vihnin.utils.ServiceUtils.emptyResponse;

public final class ServerUtils {
    private ServerUtils() {

    }

    public static HttpResponse<byte[]> handleSingleAcknowledgment(
            ResponseAccumulator responseAccumulator,
            HttpResponse<byte[]> httpResponse,
            Throwable throwable) {
        if (throwable == null) {
            if (httpResponse == null) {
                responseAccumulator.acknowledgeFailed();
            } else {
                Optional<String> time = httpResponse.headers()
                        .firstValue(TIME_HEADER_NAME);
                if (time.isPresent()) {
                    responseAccumulator.acknowledgeSucceed(
                            Long.parseLong(time.get()),
                            httpResponse.statusCode(),
                            httpResponse.body()
                    );
                } else {
                    responseAccumulator.acknowledgeFailed();
                }
            }
            return httpResponse;
        } else {
            responseAccumulator.acknowledgeMissed();
            return null;
        }
    }

    public static boolean methodAllowed(int method) {
        return method == Request.METHOD_GET
                || method == Request.METHOD_DELETE
                || method == Request.METHOD_PUT;
    }

    public static void processAcknowledgment(
            int method,
            HttpSession session,
            boolean reachAckNumber,
            int freshestStatus,
            byte[] freshestData) {
        try {
            if (reachAckNumber) {
                if (method == Request.METHOD_DELETE) {
                    session.sendResponse(emptyResponse("202 Accepted"));
                } else if (method == Request.METHOD_PUT) {
                    session.sendResponse(emptyResponse("201 Created"));
                } else if (method == Request.METHOD_GET) {
                    if (freshestStatus == 200) {
                        session.sendResponse(new Response("200 OK", freshestData));
                    } else {
                        session.sendResponse(emptyResponse("404 Not Found"));
                    }
                }
            } else {
                session.sendResponse(emptyResponse("504 Not Enough Replicas"));
            }
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    public static class ChunkedResponse extends Response {
        public Iterator<Row<String, byte[]>> iterator;

        public ChunkedResponse(String resultCode, Iterator<Row<String, byte[]>> iterator) {
            super(resultCode);
            this.iterator = iterator;
            this.addHeader("Transfer-Encoding: chunked");
        }
    }

    public static final class ChunkItem extends Session.QueueItem {
        static byte[] delimiter = Utf8.toBytes("\n");
        static byte[] chunkDelimiter = Utf8.toBytes("\r\n");
        private final Row<String, byte[]> row;

        public ChunkItem(Row<String, byte[]> row) {
            super();
            this.row = row;
        }

        @Override
        public int write(Socket socket) throws IOException {
            ByteArrayBuilder body = new ByteArrayBuilder();

            if (row == null) {
                body.append(Utf8.toBytes(Integer.toHexString(0)));
                body.append(chunkDelimiter);
                body.append(chunkDelimiter);
            } else {
                byte[] key = Utf8.toBytes(row.getKey());
                byte[] value = ResponseManager.parseActualDataFromData(row.getValue());

                int chunkSize = key.length + delimiter.length + value.length;

                body.append(Utf8.toBytes(Integer.toHexString(chunkSize)));
                body.append(chunkDelimiter);
                body.append(key);
                body.append(delimiter);
                body.append(value);
                body.append(chunkDelimiter);
            }

            byte[] bytes = body.toBytes();

            return socket.write(bytes, 0, bytes.length);
        }
    }
}
