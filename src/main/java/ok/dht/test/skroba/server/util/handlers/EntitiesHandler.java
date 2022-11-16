package ok.dht.test.skroba.server.util.handlers;

import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.server.response.ChunkedResponse;
import ok.dht.test.skroba.shard.Manager;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public final class EntitiesHandler extends AbstractEntityHandler {
    private static final String START = "start=";
    private static final String END = "end=";
    
    public EntitiesHandler(final Manager manager, final EntityDao dao) {
        super(manager, dao);
    }
    
    @Override
    public void handle(final Request request, final HttpSession session, final ExecutorService service)
            throws IOException {
        if (request.getMethod() != Request.METHOD_GET) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        
        String start = request.getParameter(START);
        String end = request.getParameter(END);
        
        if (start == null || start.isBlank() || (end != null && end.isBlank())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        byte[] startBytes = Utf8.toBytes(start);
        byte[] endBytes = end == null ? null : Utf8.toBytes(end);
        
        DBIterator iterator = dao.iterator();
        
        iterator.seek(startBytes);
        
        session.sendResponse(new ChunkedResponse(Response.OK, iterator, endBytes));
    }
}
