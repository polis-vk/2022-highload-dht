package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;

public interface CustomService extends Service {

    void handleGet(HandlerRequest request, HandlerResponse response);

    void handlePut(HandlerRequest request, HandlerResponse response);

    void handleDelete(HandlerRequest request, HandlerResponse response);
}
