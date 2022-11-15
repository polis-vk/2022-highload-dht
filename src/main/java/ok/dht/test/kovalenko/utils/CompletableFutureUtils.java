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

        MyHttpSession session = extendedSubscription.base().session();
        AtomicInteger acks = extendedSubscription.acks();
        int ack = session.getReplicas().ack();

        if (acks.get() >= ack && responseSent.compareAndSet(false, true)) {
            finishAggregation(session, extendedSubscription.goodResponsesBuffer().peek());
            return true;
        }

        int remainedResponses = session.getReplicas().from()
                - extendedSubscription.goodResponsesBuffer().size()
                - extendedSubscription.badResponsesBuffer().size();

        if (acks.get() + remainedResponses < ack && responseSent.compareAndSet(false, true)) {
            finishAggregation(session, MyHttpResponse.notEnoughReplicas());
            return true;
        }

        return responseSent.get();
    }

    public static void whenCancelledSubscription(Throwable t, Subscription subscription) {
        notifyAboutError(subscription, t);
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
        HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
    }

    public static void whenCancelledAggregation(Throwable t, ExtendedSubscription extendedSubscription) {
        notifyAboutError(extendedSubscription.base(), t);
        MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
        extendedSubscription.badResponsesBuffer().add(myHttpResponse);
        checkForResponseSent(extendedSubscription);
    }

    public static void notifyAboutError(Subscription subscription, Throwable t) {
        log.error("Node {} is ill", subscription.slaveNodeUrl(), t);
    }

    private static void finishAggregation(MyHttpSession session, MyHttpResponse response) {
        HttpUtils.NetRequest netRequest = () -> session.sendResponse(response);
        HttpUtils.safeHttpRequest(session, log, netRequest);
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
