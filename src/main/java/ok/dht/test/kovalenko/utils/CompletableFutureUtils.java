package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompletableFutureUtils {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureUtils.class);

    private CompletableFutureUtils() {
    }

    public static boolean checkForResponseSent(ExtendedSubscription extendedSubscription) {
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

    public static void whenCancelled(Throwable t, Subscription subscription) {
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        makeNodeIll(subscription, t.getMessage());
        HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
        HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
    }

    public static void whenCancelled(Throwable t, ExtendedSubscription extendedSubscription) {
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        extendedSubscription.badResponsesBuffer().add(myHttpResponse);
        makeNodeIll(extendedSubscription.base(), t.getMessage());
        checkForResponseSent(extendedSubscription);
    }

    public static void makeNodeIll(Subscription subscription, String cause) {
        subscription.loadBalancer().makeNodeIll(subscription.slaveNodeUrl());
        log.error("Node {} is ill cause {}", subscription.slaveNodeUrl(), cause);
    }

    public record Subscription(CompletableFuture<?> cf, MyHttpSession session, LoadBalancer loadBalancer,
                               String slaveNodeUrl) {
        // intentionally-blank
    }

    public record ExtendedSubscription(Subscription base, AtomicInteger acks, AtomicBoolean responseSent,
                                       PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                                       PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer) {
        // intentionally-blank
    }
}
