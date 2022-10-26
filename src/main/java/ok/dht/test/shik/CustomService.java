package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.test.shik.events.HandlerDeleteRequest;
import ok.dht.test.shik.events.HandlerPutRequest;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;

public interface CustomService extends Service {

    void handleGet(HandlerRequest request, HandlerResponse response);

    void handleLeaderGet(HandlerRequest request, HandlerResponse response);

    void handlePut(HandlerPutRequest request, HandlerResponse response);

    void handleLeaderPut(HandlerRequest request, HandlerResponse response);

    void handleDelete(HandlerDeleteRequest request, HandlerResponse response);

    void handleLeaderDelete(HandlerRequest request, HandlerResponse response);
}
