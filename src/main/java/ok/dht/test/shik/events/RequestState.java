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

    boolean onResponseSuccess(Response response, String url);

    boolean isSuccess();

    boolean isLeader();

    boolean isDigestOnly();

    boolean isRepairRequest();
}
