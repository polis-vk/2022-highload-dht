package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

public interface RequestState {

    Request getRequest();

    HttpSession getSession();

    String getId();

    long getTimestamp();

    boolean onResponseFailure();

    /**
     * @return <code>true</code> if current thread must trigger leader request handler
     * It happens if all <code>ack</code> requests are success or
     * all <code>from</code> requests are finished.
     */
    boolean onResponseSuccess(Response response);

    boolean isSuccess();

    boolean isLeader();
}
