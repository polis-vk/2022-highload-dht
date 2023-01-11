package ok.dht.test.shakhov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;

import java.time.Clock;

@ServiceFactory(stage = 5, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
public class StorageServiceFactory implements ServiceFactory.Factory {

    @Override
    public Service create(ServiceConfig config) {
        return new KeyValueService(config, Clock.systemUTC());
    }
}
