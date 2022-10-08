package ok.dht.test.lutsenko.service;

import ok.dht.test.lutsenko.dao.PersistenceRangeDao;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

public class DaoHttpServer extends HttpServer {

    private final PersistenceRangeDao dao = new PersistenceRangeDao(DaoConfig.defaultConfig());
    private final ExecutorService requestExecutor = RequestExecutorService.requestExecutorDiscard();
    private static final Logger LOG = LoggerFactory.getLogger(DaoHttpServer.class);

    public DaoHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String path = request.getPath();
        if (!"/v0/entity".equals(path)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
        requestExecutor.execute(new SessionRunnable(session,
                () -> {
                    Response response = proceed(request);
                    ServiceUtils.sendResponse(session, response);
                }
        ));
    }

    @Override
    public synchronized void stop() {
        try {
            RequestExecutorService.shutdownAndAwaitTermination(requestExecutor);
        } catch (TimeoutException e) {
            LOG.warn("Request executor await termination too long");
        }
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }
        super.stop();
    }

    public PersistenceRangeDao getDao() {
        return dao;
    }

    private Response proceed(Request request) {
        String id = request.getParameter("id=");
        if (id == null || id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                BaseEntry<String> entry = dao.get(id);
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, Base64.getDecoder().decode(entry.value()));
            }
            case Request.METHOD_PUT -> {
                dao.upsert(new BaseEntry<>(id, Base64.getEncoder().encodeToString(request.getBody())));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new BaseEntry<>(id, null));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }
}
