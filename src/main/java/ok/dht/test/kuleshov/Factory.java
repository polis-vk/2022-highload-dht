package ok.dht.test.kuleshov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;

@ServiceFactory(stage = 7, week = 2)
public class Factory implements ServiceFactory.Factory {
    @Override
    public Service create(ServiceConfig config) {
        return new ok.dht.test.kuleshov.Service(config);
    }
}
