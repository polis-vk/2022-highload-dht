package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.events.HandlerTimedRequest;

public interface CustomService extends Service {

    void handleGet(HandlerRequest request, HandlerResponse response);

    void handleLeaderGet(HandlerRequest request, HandlerResponse response);

    void handlePut(HandlerTimedRequest request, HandlerResponse response);

    void handleLeaderPut(HandlerRequest request, HandlerResponse response);

    void handleDelete(HandlerTimedRequest request, HandlerResponse response);

    void handleLeaderDelete(HandlerRequest request, HandlerResponse response);
}
