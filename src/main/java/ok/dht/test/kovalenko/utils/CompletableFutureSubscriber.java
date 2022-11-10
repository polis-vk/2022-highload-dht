package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableFutureSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);
    private final LoadBalancer loadBalancer;

    public CompletableFutureSubscriber(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public void subscribe(CompletableFuture<?> cf, MyHttpSession session, String masterNodeUrl, String slaveNodeUrl, boolean wasMasterNode, AtomicInteger acks,
                          PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer, PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        cf.thenAccept((response) -> {
            if (checkForExit(acks, session, goodResponsesBuffer, badResponsesBuffer)) {
                return;
            }

            MyHttpResponse myHttpResponse;
            if (response instanceof MyHttpResponse) {
                myHttpResponse = (MyHttpResponse) response;
            } else {
                HttpResponse<byte[]> httpResponse = (HttpResponse<byte[]>) response;
                myHttpResponse = HttpUtils.toMyHttpResponse(httpResponse);
            }

            if (isGoodResponse(myHttpResponse)) {
                acks.incrementAndGet();
                goodResponsesBuffer.add(myHttpResponse);
            } else {
                badResponsesBuffer.add(myHttpResponse);
                loadBalancer.makeNodeIll(slaveNodeUrl);
                log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(myHttpResponse.getBody())));
            }

            checkForExit(acks, session, goodResponsesBuffer, badResponsesBuffer);
        });
    }

    private boolean checkForExit(AtomicInteger acks, MyHttpSession session,
                                 PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer, PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        int ack = session.getReplicas().ack();

        if (acks.get() >= ack) {
            HttpUtils.NetRequest netRequest = () -> session.sendResponse(goodResponsesBuffer.peek());
            HttpUtils.safeHttpRequest(session, log, netRequest);
            return true; // FIXME processing bad responses
        }

        if (badResponsesBuffer.size() >= ack) { // No quorum
            HttpUtils.NetRequest netRequest = () -> session.sendResponse(MyHttpResponse.notEnoughReplicas());
            HttpUtils.safeHttpRequest(session, log, netRequest);
            return true;
        }

        return false;
    }

    private static boolean isGoodResponse(MyHttpResponse response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }
}
