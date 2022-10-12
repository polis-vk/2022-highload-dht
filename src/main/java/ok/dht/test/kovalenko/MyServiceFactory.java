package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;

import java.io.IOException;

@ServiceFactory(stage = 1, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
public class MyServiceFactory implements ServiceFactory.Factory {

    @Override
    public Service create(ServiceConfig config) {
        try {
            return new MyService(config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
