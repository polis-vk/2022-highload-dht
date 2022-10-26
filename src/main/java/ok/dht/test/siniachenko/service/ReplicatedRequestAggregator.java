package ok.dht.test.siniachenko.service;

import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.Utils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.atomic.AtomicInteger;

public class ReplicatedRequestAggregator {
    public void addResultAndAggregateIfNeed(
        HttpSession session, int ack, byte[][] bodies,
        AtomicInteger ackReceivedRef, byte[] value, int method
    ) {
        // saving received body and status code
        int ackReceived;
        while (true) {
            ackReceived = ackReceivedRef.get();
            if (ackReceived >= ack) {
                return;
            }
            if (ackReceivedRef.compareAndSet(ackReceived, ackReceived + 1)) {
                bodies[ackReceived] = value;
                break;
            }
        }

        // aggregating results after receiving enough replicas
        if (ackReceived + 1 == ack) {
            if (method == Request.METHOD_GET) {
                aggregateGet(session, ack, bodies);
            }
            if (method == Request.METHOD_PUT) {
                TycoonHttpServer.sendResponse(session, new Response(Response.CREATED, Response.EMPTY));
            }
            if (method == Request.METHOD_DELETE) {
                TycoonHttpServer.sendResponse(session, new Response(Response.ACCEPTED, Response.EMPTY));
            }
        }
    }

    public void aggregateGet(HttpSession session, int ack, byte[][] bodies) {
        int maxTimeStampReplica = -1;
        long maxTimeMillis = 0;
        for (int replicaAnswered = 0; replicaAnswered < ack; replicaAnswered++) {
            if (bodies[replicaAnswered] != null) {
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
            TycoonHttpServer.sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            byte[] realValue = Utils.readValueFromBytes(bodies[maxTimeStampReplica]);
            TycoonHttpServer.sendResponse(session, new Response(Response.OK, realValue));
        }
    }
}
