package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CompletableFutureSubscriber implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureSubscriber.class);
    private final PoolKeeper poolKeeper;
    private static final int N_WORKERS = 2 * (Runtime.getRuntime().availableProcessors() + 1);
    private static final int QUEUE_CAPACITY = 10 * N_WORKERS;

    public CompletableFutureSubscriber() {
        this.poolKeeper = new PoolKeeper(
                new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                    60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Integer.MAX_VALUE),
                    new ThreadPoolExecutor.AbortPolicy()),
                3*60);
    }

    public void subscribe(CompletableFuture<MyHttpResponse> cf, MyHttpSession session, LoadBalancer loadBalancer, String slaveNodeUrl) {
        cf.thenAccept((response) -> {
                if (!isGoodResponse(response)) {
                    loadBalancer.makeNodeIll(slaveNodeUrl);
                    log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(response.getBody())));
                }

                HttpUtils.NetRequest netRequest = () -> session.sendResponse(response);
                HttpUtils.safeHttpRequest(session, log, netRequest);
            }
        ).exceptionally((throwable) -> {
                MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);

                loadBalancer.makeNodeIll(slaveNodeUrl);
                log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(throwable.getStackTrace())));
                HttpUtils.NetRequest netRequest = () -> session.sendResponse(myHttpResponse);
                HttpUtils.safeHttpRequest(session, log, netRequest);

                return null;
            }
        );
    }

    public void subscribeAsync(CompletableFuture<?> cf, MyHttpSession session, LoadBalancer loadBalancer,
                                 String slaveNodeUrl, AtomicInteger acks, AtomicBoolean responseSent,
                          PriorityBlockingQueue<MyHttpResponse> goodResponsesBuffer,
                          PriorityBlockingQueue<MyHttpResponse> badResponsesBuffer)
            throws ExecutionException, InterruptedException {
        cf.thenAccept((response) -> {
                if (checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer)) {
                    return;
                }

                MyHttpResponse myHttpResponse = MyHttpResponse.convert(response);

                if (isGoodResponse(myHttpResponse)) {
                    acks.incrementAndGet();
                    goodResponsesBuffer.add(myHttpResponse);
                } else {
                    badResponsesBuffer.add(myHttpResponse);
                    loadBalancer.makeNodeIll(slaveNodeUrl);
                    log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(myHttpResponse.getBody())));
                }

                checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer);
            }
        ).exceptionally((throwable) -> {
                MyHttpResponse myHttpResponse = new MyHttpResponse(Response.GATEWAY_TIMEOUT);
                badResponsesBuffer.add(myHttpResponse);
                loadBalancer.makeNodeIll(slaveNodeUrl);
                log.error("Node {} is ill", slaveNodeUrl, new Exception(Arrays.toString(throwable.getStackTrace())));

                checkForResponseSent(acks, responseSent, session, goodResponsesBuffer, badResponsesBuffer);
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

    @Override
    public void close() throws IOException {
        poolKeeper.close();
    }
}
