package ok.dht.test.kovalenko.utils;

import one.nio.http.HttpSession;

import java.net.http.HttpResponse;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

public class CompletableFutureSubscriber {

    public static void subscribe(CompletableFuture<?> cf, HttpSession session, Semaphore semaphore,
                                 Queue<?> goodResponsesBuffer) {

    }


}
