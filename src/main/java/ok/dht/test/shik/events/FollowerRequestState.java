package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FollowerRequestState extends AbstractRequestState {

    private static final Log LOG = LogFactory.getLog(FollowerRequestState.class);
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final long TIMEOUT_MILLIS = 10 * 1000;

    private final CompletableFuture<Response> responseFuture;
    private volatile Response readyResponse;

    public FollowerRequestState(Request request, HttpSession session, String id) {
        super(request, session, id);
        responseFuture = new CompletableFuture<>();
    }

    public Response getResponse() {
        try {
            return responseFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Leader interrupted while waiting for responses from other shards", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.error("Exception while waiting for setting completable future", e);
        } catch (TimeoutException e) {
            LOG.error("Timeout while waiting for setting completable future", e);
        }
        return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
    }

    @Override
    public void awaitShardResponses() {
        getResponse();
    }

    @Override
    public void onShardResponseFailure() {
        // no operations
    }

    @Override
    public void onShardResponseSuccess(Response response) {
        readyResponse = response;
        responseFuture.complete(response);
    }

    @Override
    public boolean isSuccess() {
        return readyResponse != null;
    }
}
