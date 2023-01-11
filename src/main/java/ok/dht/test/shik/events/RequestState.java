package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

public interface RequestState {

    void awaitShardResponses();

    Request getRequest();

    HttpSession getSession();

    String getId();

    void onShardResponseFailure();

    void onShardResponseSuccess(Response response);

    boolean isSuccess();
}
