package ok.dht.test.skroba.server.util.handlers;

import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.shard.Manager;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public final class InternalEntityHandler extends AbstractEntityHandler {
    public InternalEntityHandler(final Manager manager, final EntityDao dao) {
        super(manager, dao);
    }
    
    @Override
    public void handle(final Request request, final HttpSession session, final ExecutorService service)
            throws IOException {
        final String id = request.getParameter(ID_PARAMETER);
        
        if (id == null || id.isBlank()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        handleDbOperation(session, request.getMethod(), id, request.getBody());
    }
}
