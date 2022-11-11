package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CompletableFutureSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);
    private final LoadBalancer loadBalancer;
    private final Executor executor;
    private static final int N_WORKERS = 2 * (Runtime.getRuntime().availableProcessors() + 1);
    private static final int QUEUE_CAPACITY = 10 * N_WORKERS;

    public CompletableFutureSubscriber(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        this.executor = new ThreadPoolExecutor(1, N_WORKERS,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void subscribe(CompletableFuture<?> cf, MyHttpSession session, String slaveNodeUrl,
                          AtomicInteger acks, AtomicBoolean responseSent,
                          PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                          PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer)
            throws ExecutionException, InterruptedException {
        AtomicReference<MyHttpResponse> lastResponse = new AtomicReference<>(null);

        cf
            .thenAcceptAsync((response) -> {
                if (checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer)) {
                    return;
                }

                if (response instanceof MyHttpResponse) {
                    lastResponse.set((MyHttpResponse) response);
                } else {
                    lastResponse.set(HttpUtils.toMyHttpResponse((HttpResponse<byte[]>) response));
                }

                MyHttpResponse myHttpResponse = lastResponse.get();

                if (isGoodResponse(myHttpResponse)) {
                    acks.incrementAndGet();
                    goodResponsesBuffer.add(myHttpResponse);
                } else {
                    badResponsesBuffer.add(myHttpResponse);
                    loadBalancer.makeNodeIll(slaveNodeUrl);
                    log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(myHttpResponse.getBody())));
                }

                checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer);
            }, executor)
        .exceptionallyAsync((throwable) -> {
            MyHttpResponse myHttpResponse = lastResponse.get();
            if (myHttpResponse == null) {
                myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
            }
            badResponsesBuffer.add(myHttpResponse);
            loadBalancer.makeNodeIll(slaveNodeUrl);
            log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(throwable.getStackTrace())));

            checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer);
            return null;
        }, executor);
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

    private static boolean isGoodResponse(MyHttpResponse response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }
}
