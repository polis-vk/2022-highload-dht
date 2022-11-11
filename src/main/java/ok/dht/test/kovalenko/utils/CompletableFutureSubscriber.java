package ok.dht.test.kovalenko.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class CompletableFutureSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);

    public void subscribe(CompletableFutureUtils.Subscription subscription) {
        subscription.cf().thenAccept((response) -> {
                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (!HttpUtils.isGoodResponse(myHttpResponse)) {
                    CompletableFutureUtils.makeNodeIll(subscription, Arrays.toString(myHttpResponse.getBody()));
                }

                HttpUtils.NetRequest netRequest = () -> subscription.session().sendResponse(myHttpResponse);
                HttpUtils.safeHttpRequest(subscription.session(), log, netRequest);
            }
        ).exceptionally((throwable) -> {
            CompletableFutureUtils.whenCancelled(throwable, subscription);
                return null;
            }
        );
    }

    public void subscribe(CompletableFutureUtils.ExtendedSubscription extendedSubscription) {
        CompletableFutureUtils.Subscription base = extendedSubscription.base();
        base.cf().thenAccept((response) -> {
                if (CompletableFutureUtils.checkForResponseSent(extendedSubscription)) {
                    return;
                }

                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (HttpUtils.isGoodResponse(myHttpResponse)) {
                    extendedSubscription.acks().incrementAndGet();
                    extendedSubscription.goodResponsesBuffer().add(myHttpResponse);
                } else {
                    extendedSubscription.badResponsesBuffer().add(myHttpResponse);
                    CompletableFutureUtils.makeNodeIll(base, Arrays.toString(myHttpResponse.getBody()));
                }

                CompletableFutureUtils.checkForResponseSent(extendedSubscription);
            }
        ).exceptionally((throwable) -> {
            CompletableFutureUtils.whenCancelled(throwable, extendedSubscription);
                return null;
            }
        );
    }
}
