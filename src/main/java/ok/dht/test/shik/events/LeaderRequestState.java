package ok.dht.test.shik.events;

import ok.dht.test.shik.consistency.InconsistencyResolutionStrategy;
import one.nio.http.Response;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaderRequestState extends AbstractRequestState {

    private final AtomicInteger remainToSuccess;
    private final Map<String, Response> replicaResponses;
    private final List<String> nodeUrls;
    private final int requestedReplicas;
    private final int requiredReplicas;
    private final AtomicBoolean completed;
    private final InconsistencyResolutionStrategy inconsistencyStrategy;
    private final String mainNodeUrl;

    public LeaderRequestState(CommonRequestStateParams commonParams,
                              int requestedReplicas, int requiredReplicas, List<String> nodeUrls, String mainNodeUrl,
                              InconsistencyResolutionStrategy inconsistencyStrategy) {
        super(commonParams);
        this.requiredReplicas = requiredReplicas;
        this.nodeUrls = nodeUrls;
        this.mainNodeUrl = mainNodeUrl;
        this.requestedReplicas = requestedReplicas;
        remainToSuccess = new AtomicInteger(requestedReplicas);
        replicaResponses = new ConcurrentHashMap<>();
        completed = new AtomicBoolean(false);
        this.inconsistencyStrategy = inconsistencyStrategy;
    }

    public LeaderRequestState(LeaderRequestState state, InconsistencyResolutionStrategy strategy) {
        this(new CommonRequestStateParams(state.getRequest(), state.getSession(), state.getId(), state.getTimestamp()),
            state.requestedReplicas, state.requiredReplicas, state.nodeUrls, state.mainNodeUrl, strategy);

    }

    public Map<String, Response> getReplicaResponses() {
        return replicaResponses;
    }

    @Override
    public boolean onResponseFailure() {
        return onResponse(null, null);
    }

    @Override
    public boolean onResponseSuccess(Response response, String url) {
        return onResponse(response, url);
    }

    private boolean onResponse(Response response, String url) {
        while (true) {
            boolean currentCompleted = completed.get();
            int currentRemain = remainToSuccess.get();

            if (currentCompleted || currentRemain == 0) {
                return false;
            }

            if (remainToSuccess.compareAndSet(currentRemain, currentRemain - 1)) {
                return processAfterSuccessCas(currentRemain, response, url);
            }
        }
    }

    private boolean processAfterSuccessCas(int currentRemain, Response response, String url) {
        if (response != null) {
            replicaResponses.put(url, response);
        }

        return casCompleted(currentRemain);
    }

    private boolean casCompleted(int currentRemain) {
        return currentRemain == 1 && completed.compareAndSet(false, true);
    }

    public InconsistencyResolutionStrategy getInconsistencyStrategy() {
        return inconsistencyStrategy;
    }

    public List<String> getNodeUrls() {
        return nodeUrls;
    }

    public String getMainNodeUrl() {
        return mainNodeUrl;
    }

    @Override
    public boolean isSuccess() {
        return replicaResponses.size() >= requiredReplicas;
    }

    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public boolean isDigestOnly() {
        return false;
    }

    @Override
    public boolean isRepairRequest() {
        return false;
    }
}
