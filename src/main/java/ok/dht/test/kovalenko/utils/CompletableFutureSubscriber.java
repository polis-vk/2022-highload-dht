package ok.dht.test.kovalenko.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class CompletableFutureSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);

    public void subscribe(CompletableFutureUtils.Subscription subscription) {
        subscription.cf()
                .thenAccept((response) -> {
                    MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                    if (!HttpUtils.isGoodResponse(myHttpResponse)) {
                        CompletableFutureUtils.notifyAboutError(subscription, extractException(myHttpResponse));
                    }

                    HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
                    HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
                })
                .exceptionally((throwable) -> {
                    CompletableFutureUtils.whenCancelledSubscription(throwable, subscription);
                    return null;
                });
    }

    public void aggregate(CompletableFutureUtils.ExtendedSubscription extendedSubscription) {
        CompletableFutureUtils.Subscription base = extendedSubscription.base();
        base.cf()
                .thenAccept((response) -> {
                    if (CompletableFutureUtils.checkForResponseSent(extendedSubscription)) {
                        return;
                    }

                    MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                    if (HttpUtils.isGoodResponse(myHttpResponse)) {
                        extendedSubscription.acks().incrementAndGet();
                        extendedSubscription.goodResponsesBuffer().add(myHttpResponse);
                    } else {
                        extendedSubscription.badResponsesBuffer().add(myHttpResponse);
                        CompletableFutureUtils.notifyAboutError(base, extractException(myHttpResponse));
                    }

                    CompletableFutureUtils.checkForResponseSent(extendedSubscription);
                })
                .exceptionally((throwable) -> {
                    CompletableFutureUtils.whenCancelledAggregation(throwable, extendedSubscription);
                    return null;
                });
    }

    private static Exception extractException(MyHttpResponse r) {
        return new Exception(Arrays.toString(r.getBody()));
    }
}
