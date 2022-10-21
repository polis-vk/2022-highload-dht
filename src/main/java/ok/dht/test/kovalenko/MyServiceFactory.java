package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;

import java.io.IOException;

@ServiceFactory(stage = 3, week = 1)
public class MyServiceFactory implements ServiceFactory.Factory {

    @Override
    public Service create(ServiceConfig config) {
        try {
            return new MyServiceBase(config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
