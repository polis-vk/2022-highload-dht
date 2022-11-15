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

    public Response proceed(String id, Request request, Long requestTime) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> proceedGet(id);
                case Request.METHOD_PUT -> proceedPut(id, request.getBody(), requestTime);
                case Request.METHOD_DELETE -> proceedDelete(id, requestTime);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (Exception e) {
            LOG.error("Failed to proceed request in dao", e);
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    public void handle(Request request, HttpSession session, String id, Long requestTime) {
        ServiceUtils.sendResponse(session, proceed(id, request, requestTime));
    }

    private Response proceedGet(String id) {
        BaseEntry<String> entry = dao.get(id);
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        Response response = (entry.value() == null)
                ? new Response(Response.NOT_FOUND, Response.EMPTY)
                : new Response(Response.OK, Base64.getDecoder().decode(entry.value()));
        response.addHeader(CustomHeaders.REQUEST_TIME + entry.requestTime());
        return response;
    }

    private Response proceedPut(String id, byte[] body, long requestTime) {
        dao.upsert(new BaseEntry<>(requestTime, id, Base64.getEncoder().encodeToString(body)));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response proceedDelete(String id, long requestTime) {
        dao.upsert(new BaseEntry<>(requestTime, id, null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    public PersistenceRangeDao getDao() {
        return dao;
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }
}
