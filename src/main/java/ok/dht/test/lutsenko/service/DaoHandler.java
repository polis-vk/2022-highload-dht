package ok.dht.test.lutsenko.service;

import ok.dht.test.lutsenko.dao.PersistenceRangeDao;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Base64;

public class DaoHandler implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final PersistenceRangeDao dao;

    public DaoHandler(DaoConfig config) throws IOException {
        dao = new PersistenceRangeDao(config);
    }

    public void handle(String id, Request request, HttpSession session) {
        try {
            Response response = switch (request.getMethod()) {
                case Request.METHOD_GET -> proceedGet(id);
                case Request.METHOD_PUT -> proceedPut(id, request.getBody());
                case Request.METHOD_DELETE -> proceedDelete(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
            ServiceUtils.sendResponse(session, response);
        } catch (Exception e) {
            LOG.error("Failed to proceed request in dao", e);
            ServiceUtils.sendResponse(session, Response.SERVICE_UNAVAILABLE);
        }
    }

    private Response proceedGet(String id) {
        BaseEntry<String> entry = dao.get(id);
        return entry == null
                ? new Response(Response.NOT_FOUND, Response.EMPTY)
                : new Response(Response.OK, Base64.getDecoder().decode(entry.value()));
    }

    private Response proceedPut(String id, byte[] body) {
        dao.upsert(new BaseEntry<>(id, Base64.getEncoder().encodeToString(body)));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response proceedDelete(String id) {
        dao.upsert(new BaseEntry<>(id, null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }
}
