package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableFutureSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);

    public void subscribe(Subscription subscription) {
        subscription.cf().thenAccept((response) -> {
                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (!isGoodResponse(myHttpResponse)) {
                    subscription.loadBalancer().makeNodeIll(subscription.slaveNodeUrl());
                    log.error("Node {} is ill", subscription.slaveNodeUrl(), new Exception(Arrays.toString(myHttpResponse.getBody())));
                }

                HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
                HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
            }
        ).exceptionally((throwable) -> {
                whenCancelled(throwable, subscription);
                return null;
            }
        );
    }

    public void subscribe(Subscription subscription, AtomicInteger acks, AtomicBoolean responseSent,
                          PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                          PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        subscription.cf().thenAccept((response) -> {
                if (checkForResponseSent(acks, responseSent, subscription.session(), goodResponsesBuffer, badResponsesBuffer)) {
                    return;
                }

                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (isGoodResponse(myHttpResponse)) {
                    acks.incrementAndGet();
                    goodResponsesBuffer.add(myHttpResponse);
                } else {
                    badResponsesBuffer.add(myHttpResponse);
                    subscription.loadBalancer().makeNodeIll(subscription.slaveNodeUrl());
                    log.error("Node {} is ill", subscription.slaveNodeUrl(),
                            new Exception(Arrays.toString(myHttpResponse.getBody())));
                }

                checkForResponseSent(acks, responseSent, subscription.session(), goodResponsesBuffer, badResponsesBuffer);
            }
        ).exceptionally((throwable) -> {
                whenCancelled(throwable, subscription, acks, responseSent, goodResponsesBuffer, badResponsesBuffer);
                return null;
            }
        );
    }

    private boolean checkForResponseSent(AtomicInteger acks, AtomicBoolean responseSent, MyHttpSession session,
                                         PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                                         PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        if (responseSent.get()) {
            return true;
        }

        int ack = session.getReplicas().ack();

        if (acks.get() >= ack && responseSent.compareAndSet(false, true)) {
            HttpUtils.NetRequest netRequest = () -> session.sendResponse(goodResponsesBuffer.peek());
            HttpUtils.safeHttpRequest(session, log, netRequest);
            return true;
        }

        int remainedResponses = session.getReplicas().from() - goodResponsesBuffer.size() - badResponsesBuffer.size();

        if (acks.get() + remainedResponses < ack && responseSent.compareAndSet(false, true)) {
            HttpUtils.NetRequest netRequest = () -> session.sendResponse(MyHttpResponse.notEnoughReplicas());
            HttpUtils.safeHttpRequest(session, log, netRequest);
            return true;
        }

        return responseSent.get();
    }

    private boolean isGoodResponse(MyHttpResponse response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    private void whenCancelled(Throwable t, Subscription subscription) {
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        subscription.loadBalancer().makeNodeIll(subscription.slaveNodeUrl());
        log.error("Node {} is ill", subscription.slaveNodeUrl(), new Exception(Arrays.toString(t.getStackTrace())));
        HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
        HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
    }

    private void whenCancelled(Throwable t, Subscription subscription, AtomicInteger acks,
                                     AtomicBoolean responseSent,
                                     PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                                     PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        badResponsesBuffer.add(myHttpResponse);
        subscription.loadBalancer().makeNodeIll(subscription.slaveNodeUrl());
        log.error("Node {} is ill", subscription.slaveNodeUrl(), new Exception(Arrays.toString(t.getStackTrace())));
        checkForResponseSent(acks, responseSent, subscription.session(), goodResponsesBuffer, badResponsesBuffer);
    }

    public record Subscription(CompletableFuture<?> cf, MyHttpSession session, LoadBalancer loadBalancer,
                               String slaveNodeUrl) {
    }
}
