package ok.dht.test.skroba.server.util;

import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.server.util.handlers.EntityHandler;
import ok.dht.test.skroba.server.util.handlers.InternalEntityHandler;
import ok.dht.test.skroba.server.util.handlers.RequestHandler;
import ok.dht.test.skroba.shard.Manager;

public enum AcceptedPaths {
    ENTITY("/v0/entity", EntityHandler::new),
    INTERNAL_ENTITY("/internal/v0/entity", InternalEntityHandler::new);
    
    private final String path;
    private final HandlerFactory factory;
    
    AcceptedPaths(final String path, final HandlerFactory factory) {
        this.path = path;
        this.factory = factory;
    }
    
    public String getPath() {
        return path;
    }
    
    public RequestHandler getHandler(Manager manager, EntityDao dao) {
        return factory.create(manager, dao);
    }
    
    @FunctionalInterface
    interface HandlerFactory {
        RequestHandler create(Manager manager, EntityDao dao);
    }
}
