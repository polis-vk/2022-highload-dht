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
                    log.error("Node {} is ill", subscription.slaveNodeUrl(),
                            new Exception(Arrays.toString(myHttpResponse.getBody())));
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

    public void subscribe(ExtendedSubscription extendedSubscription) {
        Subscription base = extendedSubscription.base();
        base.cf().thenAccept((response) -> {
                if (checkForResponseSent(extendedSubscription)) {
                    return;
                }

                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (isGoodResponse(myHttpResponse)) {
                    extendedSubscription.acks().incrementAndGet();
                    extendedSubscription.goodResponsesBuffer().add(myHttpResponse);
                } else {
                    extendedSubscription.badResponsesBuffer().add(myHttpResponse);
                    base.loadBalancer().makeNodeIll(base.slaveNodeUrl());
                    log.error("Node {} is ill", base.slaveNodeUrl(),
                            new Exception(Arrays.toString(myHttpResponse.getBody())));
                }

                checkForResponseSent(extendedSubscription);
            }
        ).exceptionally((throwable) -> {
                whenCancelled(throwable, extendedSubscription);
                return null;
            }
        );
    }

    private boolean checkForResponseSent(ExtendedSubscription extendedSubscription) {
        AtomicBoolean responseSent = extendedSubscription.responseSent();
        if (responseSent.get()) {
            return true;
        }

        Subscription base = extendedSubscription.base();
        MyHttpSession session = base.session();
        AtomicInteger acks = extendedSubscription.acks();
        int ack = session.getReplicas().ack();

        if (acks.get() >= ack && responseSent.compareAndSet(false, true)) {
            HttpUtils.NetRequest netRequest
                    = () -> session.sendResponse(extendedSubscription.goodResponsesBuffer().peek());
            HttpUtils.safeHttpRequest(session, log, netRequest);
            return true;
        }

        int remainedResponses = session.getReplicas().from()
                - extendedSubscription.goodResponsesBuffer().size()
                - extendedSubscription.badResponsesBuffer().size();

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
        makeNodeIll(t, subscription.loadBalancer(), subscription.slaveNodeUrl);
        HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
        HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
    }

    private void whenCancelled(Throwable t, ExtendedSubscription extendedSubscription) {
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        extendedSubscription.badResponsesBuffer().add(myHttpResponse);
        makeNodeIll(t, extendedSubscription.base().loadBalancer(), extendedSubscription.base().slaveNodeUrl);
        checkForResponseSent(extendedSubscription);
    }

    private void makeNodeIll(Throwable t, LoadBalancer loadBalancer, String nodeUrl) {
        loadBalancer.makeNodeIll(nodeUrl);
        log.error("Node {} is ill", nodeUrl, new Exception(Arrays.toString(t.getStackTrace())));
    }

    public record Subscription(CompletableFuture<?> cf, MyHttpSession session, LoadBalancer loadBalancer,
                               String slaveNodeUrl) {
    }

    public record ExtendedSubscription(Subscription base, AtomicInteger acks, AtomicBoolean responseSent,
                                       PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                                       PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
    }
}
