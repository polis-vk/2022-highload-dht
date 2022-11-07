package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaderRequestState extends AbstractRequestState {

    private final AtomicInteger remainOverall;
    private final AtomicInteger remainToSuccess;
    private final Queue<Response> shardResponses;
    private final AtomicBoolean completed;

    public LeaderRequestState(int requestedReplicas, int requiredReplicas,
                              Request request, HttpSession session, String id, long timestamp) {
        super(request, session, id, timestamp);
        remainOverall = new AtomicInteger(requestedReplicas);
        remainToSuccess = new AtomicInteger(requiredReplicas);
        shardResponses = new ConcurrentLinkedQueue<>();
        completed = new AtomicBoolean(false);
    }

    public Queue<Response> getShardResponses() {
        return shardResponses;
    }

    @Override
    public boolean onResponseFailure() {
        while (true) {
            boolean curCompleted = completed.get();
            int curRemainOverall = remainOverall.get();

            if (curCompleted) {
                return false;
            }

            if (remainOverall.compareAndSet(curRemainOverall, curRemainOverall - 1)) {
                return casCompleted(curRemainOverall);
            }
        }
    }

    @Override
    public boolean onResponseSuccess(Response response) {
        while (true) {
            boolean curCompleted = completed.get();
            int curRemainOverall = remainOverall.get();
            int curRemainToSuccess = remainToSuccess.get();

            if (isCompletedOnSuccess(curCompleted, curRemainToSuccess)) {
                return false;
            }

            if (remainOverall.compareAndSet(curRemainOverall, curRemainOverall - 1)) {
                return processOnSuccessAfterCas(curRemainToSuccess, response);
            }
        }
    }

    private static boolean isCompletedOnSuccess(boolean curCompleted, int curRemainToSuccess) {
        return curCompleted || curRemainToSuccess == 0;
    }

    private boolean processOnSuccessAfterCas(int curRemainToSuccess, Response response) {
        int currentRemainToSuccess = curRemainToSuccess;
        while (currentRemainToSuccess > 0
            && !remainToSuccess.compareAndSet(currentRemainToSuccess, currentRemainToSuccess - 1)) {
            currentRemainToSuccess = remainToSuccess.get();
        }
        shardResponses.add(response);

        return casCompleted(currentRemainToSuccess);
    }

    private boolean casCompleted(int curRemain) {
        return curRemain == 1 && completed.compareAndSet(false, true);
    }

    @Override
    public boolean isSuccess() {
        return remainToSuccess.get() == 0;
    }

    @Override
    public boolean isLeader() {
        return true;
    }
}
