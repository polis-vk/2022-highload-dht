package ok.dht.test.siniachenko.service;

import ok.dht.test.siniachenko.Utils;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.atomic.AtomicInteger;

public class ReplicatedRequestAggregator {
    public boolean filterSuccessfulStatusCode(int method, int statusCode) {
        if (method == Request.METHOD_GET) {
            return statusCode == 200 || statusCode == 410 || statusCode == 404;
        }
        if (method == Request.METHOD_PUT) {
            return statusCode == 201;
        }
        if (method == Request.METHOD_DELETE) {
            return statusCode == 202;
        }
        return false;
    }

    public Response addResultAndAggregateIfNeed(
        int ack, byte[][] bodies, AtomicInteger ackReceivedRef, byte[] value, int method
    ) {
        // saving received body and status code
        int ackReceived;
        while (true) {
            ackReceived = ackReceivedRef.get();
            if (ackReceived >= ack) {
                return null;
            }
            if (ackReceivedRef.compareAndSet(ackReceived, ackReceived + 1)) {
                bodies[ackReceived] = value;
                break;
            }
        }

        // aggregating results after receiving enough replicas
        if (ackReceived + 1 == ack) {
            if (method == Request.METHOD_GET) {
                return aggregateGet(ack, bodies);
            }
            if (method == Request.METHOD_PUT) {
                return new Response(Response.CREATED, Response.EMPTY);
            }
            if (method == Request.METHOD_DELETE) {
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
        }
        return null;
    }

    public Response aggregateGet(int ack, byte[][] bodies) {
        int maxTimeStampReplica = -1;
        long maxTimeMillis = 0;
        for (int replicaAnswered = 0; replicaAnswered < ack; replicaAnswered++) {
            if (bodies[replicaAnswered] != null && bodies[replicaAnswered].length != 0) {
                long timeMillis = Utils.readTimeMillisFromBytes(bodies[replicaAnswered]);
                if (maxTimeMillis < timeMillis) {
                    maxTimeMillis = timeMillis;
                    maxTimeStampReplica = replicaAnswered;
                }
            }
        }
        if (
            maxTimeStampReplica == -1 || bodies[maxTimeStampReplica] == null
                || Utils.readFlagDeletedFromBytes(bodies[maxTimeStampReplica])
        ) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            byte[] realValue = Utils.readValueFromBytes(bodies[maxTimeStampReplica]);
            return new Response(Response.OK, realValue);
        }
    }
}
