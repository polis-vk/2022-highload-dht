package ok.dht.test.skroba.server.util.handlers;

import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.db.base.Entity;
import ok.dht.test.skroba.db.exception.DaoException;
import ok.dht.test.skroba.shard.Manager;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class AbstractEntityHandler implements RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEntityHandler.class);
    protected final Manager manager;
    
    protected final EntityDao dao;
    
    public AbstractEntityHandler(final Manager manager, final EntityDao dao) {
        this.manager = manager;
        this.dao = dao;
    }
    
    protected void handleDbOperation(final HttpSession session, final int method, final String id, final byte[] body)
            throws
            IOException {
        try {
            switch (method) {
                case Request.METHOD_GET -> {
                    final byte[] entity = dao.get(id);
                    
                    if (entity == null) {
                        session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                    } else {
                        session.sendResponse(Response.ok(entity));
                    }
                }
                
                case Request.METHOD_PUT -> {
                    dao.put(id, body);
                    
                    session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                }
                
                case Request.METHOD_DELETE -> {
                    Entity entity = Entity.TOMBSTONE(Entity.deserialize(body)
                            .getTimestamp());
                    dao.put(id, entity.serialize());
                    session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                }
                
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            }
        } catch (DaoException e) {
            LOGGER.error("Dao exception occurred: " + e.getMessage());
            
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }
}
