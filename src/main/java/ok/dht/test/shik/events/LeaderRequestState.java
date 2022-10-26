package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class LeaderRequestState extends AbstractRequestState {
    private static final Log LOG = LogFactory.getLog(LeaderRequestState.class);

    private final CountDownLatch remainOverall;
    private final CountDownLatch remainToSuccess;
    private final Queue<Response> shardResponses;

    public LeaderRequestState(int requestedReplicas, int requiredReplicas,
                              Request request, HttpSession session, String id) {
        super(request, session, id);
        remainOverall = new CountDownLatch(requestedReplicas);
        remainToSuccess = new CountDownLatch(requiredReplicas);
        shardResponses = new ConcurrentLinkedQueue<>();
    }

    public void awaitShardResponses() {
        try {
            remainOverall.await();
        } catch (InterruptedException e) {
            LOG.warn("Leader interrupted while waiting for responses from other shards", e);
            Thread.currentThread().interrupt();
        }
    }

    public Queue<Response> getShardResponses() {
        return shardResponses;
    }

    @Override
    public void onShardResponseFailure() {
        remainOverall.countDown();
    }

    @Override
    public void onShardResponseSuccess(Response response) {
        shardResponses.add(response);
        remainToSuccess.countDown();
        remainOverall.countDown();

        if (remainToSuccess.getCount() == 0) {
            while (remainOverall.getCount() > 0) {
                remainOverall.countDown();
            }
        }
    }

    @Override
    public boolean isSuccess() {
        return remainToSuccess.getCount() == 0;
    }
}
