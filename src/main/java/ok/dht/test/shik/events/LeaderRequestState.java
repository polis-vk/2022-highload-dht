package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.LinkedList;
import java.util.Queue;

public class LeaderRequestState extends AbstractRequestState {

    private int remainOverall;
    private int remainToSuccess;
    private final Queue<Response> shardResponses;
    private boolean completed;

    public LeaderRequestState(int requestedReplicas, int requiredReplicas,
                              Request request, HttpSession session, String id, long timestamp) {
        super(request, session, id, timestamp);
        remainOverall = requestedReplicas;
        remainToSuccess = requiredReplicas;
        shardResponses = new LinkedList<>();
        completed = false;
    }

    public Queue<Response> getShardResponses() {
        return shardResponses;
    }

    @Override
    public synchronized boolean onResponseFailure() {
        --remainOverall;
        if (remainOverall == 0 && !completed) {
            completed = true;
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean onResponseSuccess(Response response) {
        shardResponses.add(response);
        --remainToSuccess;
        --remainOverall;
        if (remainToSuccess == 0 && !completed) {
            completed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean isSuccess() {
        return remainToSuccess == 0;
    }

    @Override
    public boolean isLeader() {
        return true;
    }
}
