package ok.dht.test.shakhov;

import one.nio.http.Response;

import java.util.List;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

public final class ResponseAggregator {

    private ResponseAggregator() {
    }

    public static Response aggregateGetResponses(Iterable<Response> responses, int ack) {
        int ackCounter = 0;
        byte[] data = null;
        long maxTimestamp = Long.MIN_VALUE;
        for (Response response : responses) {
            if (response.getStatus() == HTTP_OK || response.getStatus() == HTTP_NOT_FOUND) {
                ackCounter++;
                long timestamp = Long.parseLong(response.getHeader(HttpUtils.ONE_NIO_X_RECORD_TIMESTAMP_HEADER));
                if (timestamp > maxTimestamp) {
                    maxTimestamp = timestamp;
                    data = response.getStatus() == HTTP_OK ? response.getBody() : null;
                }
            }
        }

        if (ackCounter < ack) {
            return new Response(HttpUtils.NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        if (data == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }

        return new Response(Response.OK, data);
    }

    public static Response aggregatePutResponses(List<Response> responses, int ack) {
        return aggregateGenericResponses(responses, ack, HTTP_CREATED, Response.CREATED);
    }

    public static Response aggregateDeleteResponses(List<Response> responses, int ack) {
        return aggregateGenericResponses(responses, ack, HTTP_ACCEPTED, Response.ACCEPTED);
    }

    public static Response aggregateGenericResponses(Iterable<Response> responses,
                                                     int ack,
                                                     int expectedStatus,
                                                     String resultCode) {
        int ackCounter = 0;
        for (Response response : responses) {
            if (response.getStatus() == expectedStatus) {
                ackCounter++;
            }
        }

        if (ackCounter < ack) {
            return new Response(HttpUtils.NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        return new Response(resultCode, Response.EMPTY);
    }
}
