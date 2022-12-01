package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.CompletableFuture;

public class FollowerRequestState extends AbstractRequestState {

    private final CompletableFuture<Response> responseFuture;
    private volatile Response readyResponse;
    private final boolean digestOnly;
    private final boolean repairRequest;

    public FollowerRequestState(Request request, HttpSession session) {
        this(new CommonRequestStateParams(request, session, null, System.currentTimeMillis()), false, false);
    }

    public FollowerRequestState(CommonRequestStateParams commonParams, boolean digestOnly, boolean repairRequest) {
        super(commonParams);
        responseFuture = new CompletableFuture<>();
        this.digestOnly = digestOnly;
        this.repairRequest = repairRequest;
    }

    @Override
    public boolean onResponseFailure() {
        return true;
    }

    @Override
    public boolean onResponseSuccess(Response response, String url) {
        readyResponse = response;
        responseFuture.complete(response);
        return true;
    }

    @Override
    public boolean isSuccess() {
        return readyResponse != null;
    }

    @Override
    public boolean isLeader() {
        return false;
    }

    @Override
    public boolean isDigestOnly() {
        return digestOnly;
    }

    @Override
    public boolean isRepairRequest() {
        return repairRequest;
    }
}
