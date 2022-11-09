package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.CompletableFuture;

public class FollowerRequestState extends AbstractRequestState {

    private final CompletableFuture<Response> responseFuture;
    private volatile Response readyResponse;

    public FollowerRequestState(Request request, HttpSession session, String id, long timestamp) {
        super(request, session, id, timestamp);
        responseFuture = new CompletableFuture<>();
    }

    @Override
    public boolean onResponseFailure() {
        return true;
    }

    @Override
    public boolean onResponseSuccess(Response response) {
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
}
