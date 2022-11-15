package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReplicaResponsesHandler {

    private static final int REDUNDANT_RESPONSE_STATUS = 100; // Response.CONTINUE
    private static final String REDUNDANT_RESPONSE = REDUNDANT_RESPONSE_STATUS + " Redundant Response";
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final Logger LOG = LoggerFactory.getLogger(ReplicaResponsesHandler.class);

    private ReplicaResponsesHandler() {
    }

    public static void handle(HttpSession session,
                              RequestParser requestParser,
                              List<CompletableFuture<Response>> responsesFutures) {
        // Может возникнуть вопрос зачем нужны счетчики, ведь можно использовать размер мапы responses?
        // Чтобы не класть в мапу лишние значения, если requestTime не важен, например при PUT и DELETE.
        // Также при GET запросах с помощью CustomHeaders.REQUEST_TIME различается ситуации когда ключ не найден, тогда
        // requestTime невозможно записать, так как в dao его нет и в мапу такие ответы не кладутся, но successCounter
        // увеличивается. Когда найдена могила, то requestTime указывается и значение в мапу добавляется.
        // Если мапа пустая и CustomHeaders.REQUEST_TIME отсутствует, то requestTime принимаем за 0 и делаем одну
        // запись в мапу. Также счетчики позволяют немедленно выполнить запросы как Continue к другим репликам если
        // кворум уже набран или количество отказов гарантированно не позволит его собрать и эти задачи еще не начались.
        int ack = requestParser.getParam(RequestParser.ACK_PARAM_NAME).asInt();
        int failLimit = requestParser.getParam(RequestParser.FROM_PARAM_NAME).asInt() - ack + 1;
        AtomicInteger failsCounter = new AtomicInteger(0);
        AtomicInteger successCounter = new AtomicInteger(0);
        NavigableMap<Long, Response> responses = new ConcurrentSkipListMap<>();
        for (CompletableFuture<Response> responseFuture : responsesFutures) {
            responseFuture.thenAccept(response -> {
                if (response.getStatus() == REDUNDANT_RESPONSE_STATUS) {
                    return;
                }
                if (requestParser.successStatuses().contains(response.getStatus())) {
                    long requestTime = getRequestTime(response);
                    responses.putIfAbsent(requestTime, response);
                    finishIfSuccess(successCounter.incrementAndGet(), ack, responses, session, responsesFutures);
                } else {
                    finishIfFailed(failsCounter.incrementAndGet(), failLimit, session, responsesFutures);
                }
            }).exceptionally(throwable -> {
                finishIfFailed(failsCounter.incrementAndGet(), failLimit, session, responsesFutures);
                LOG.error("Getting response from replica failed", throwable);
                return null;
            });
        }
    }

    private static void finishIfSuccess(int successCounter,
                                        int ack,
                                        NavigableMap<Long, Response> responses,
                                        HttpSession session,
                                        List<CompletableFuture<Response>> responsesFutures) {
        if (successCounter == ack) {
            ServiceUtils.sendResponse(session, responses.lastEntry().getValue());
            completeAsRedundantNonDoneFutures(responsesFutures);
        }
    }

    private static void finishIfFailed(int failsCounter,
                                       int failLimit,
                                       HttpSession session,
                                       List<CompletableFuture<Response>> responsesFutures) {
        if (failsCounter == failLimit) {
            ServiceUtils.sendResponse(session, NOT_ENOUGH_REPLICAS);
            completeAsRedundantNonDoneFutures(responsesFutures);
        }
    }

    private static void completeAsRedundantNonDoneFutures(List<CompletableFuture<Response>> responsesFutures) {
        for (CompletableFuture<Response> responseFuture : responsesFutures) {
            if (responseFuture != null && !responseFuture.isDone()) {
                responseFuture.complete(new Response(REDUNDANT_RESPONSE, Response.EMPTY));
            }
        }
    }

    private static long getRequestTime(Response response) {
        String requestTimeHeaderValue = response.getHeader(CustomHeaders.REQUEST_TIME);
        return requestTimeHeaderValue == null ? 0L : Long.parseLong(requestTimeHeaderValue);
    }
}
