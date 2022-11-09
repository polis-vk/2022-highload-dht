package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaderRequestState extends AbstractRequestState {

    private final AtomicInteger remainToSuccess;
    private final Queue<Response> replicaResponses;
    private final int requiredReplicas;
    private final AtomicBoolean completed;

    public LeaderRequestState(int requestedReplicas, int requiredReplicas, Request request,
                              HttpSession session, String id, long timestamp) {
        super(request, session, id, timestamp);

        this.requiredReplicas = requiredReplicas;
        remainToSuccess = new AtomicInteger(requestedReplicas);
        replicaResponses = new ConcurrentLinkedQueue<>();
        completed = new AtomicBoolean(false);
    }

    public Queue<Response> getReplicaResponses() {
        return replicaResponses;
    }

    @Override
    public boolean onResponseFailure() {
        return onResponse(null);
    }

    @Override
    public boolean onResponseSuccess(Response response) {
        return onResponse(response);
    }

    private boolean onResponse(Response response) {
        while (true) {
            boolean currentCompleted = completed.get();
            int currentRemain = remainToSuccess.get();

            if (currentCompleted || currentRemain == 0) {
                return false;
            }

            if (remainToSuccess.compareAndSet(currentRemain, currentRemain - 1)) {
                return processAfterSuccessCas(currentRemain, response);
            }
        }
    }

    private boolean processAfterSuccessCas(int currentRemain, Response response) {
        if (response != null) {
            replicaResponses.add(response);
        }

        return casCompleted(currentRemain);
    }

    private boolean casCompleted(int currentRemain) {
        return currentRemain == 1 && completed.compareAndSet(false, true);
    }

    @Override
    public boolean isSuccess() {
        return replicaResponses.size() >= requiredReplicas;
    }

    @Override
    public boolean isLeader() {
        return true;
    }
}
