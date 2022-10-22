package ok.dht.test.lutsenko.service;

import ok.dht.test.lutsenko.dao.PersistenceRangeDao;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Base64;

public class DaoHandler implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final PersistenceRangeDao dao;

    public DaoHandler(DaoConfig config) throws IOException {
        dao = new PersistenceRangeDao(config);
    }

    public ResponseInfo proceed(String id, Request request, Long requestTime) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> proceedGet(id);
                case Request.METHOD_PUT -> proceedPut(id, request.getBody(), requestTime);
                case Request.METHOD_DELETE -> proceedDelete(id, requestTime);
                default -> new ResponseInfo(HttpURLConnection.HTTP_BAD_METHOD);
            };
        } catch (Exception e) {
            LOG.error("Failed to proceed request in dao", e);
            return new ResponseInfo(HttpURLConnection.HTTP_UNAVAILABLE);
        }
    }

    public void handle(String id, Request request, HttpSession session, Long requestTime) {
        ServiceUtils.sendResponse(
                session,
                ServiceUtils.toResponse(proceed(id, request, requestTime))
        );
    }

    private ResponseInfo proceedGet(String id) {
        BaseEntry<String> entry = dao.get(id);
        if (entry == null) {
            return new ResponseInfo(HttpURLConnection.HTTP_NOT_FOUND);
        }
        if (entry.value() == null) {
            return new ResponseInfo(HttpURLConnection.HTTP_NOT_FOUND, ResponseInfo.EMPTY, entry.requestTime());
        }
        return new ResponseInfo(HttpURLConnection.HTTP_OK, Base64.getDecoder().decode(entry.value()), entry.requestTime());
    }

    private ResponseInfo proceedPut(String id, byte[] body, long requestTime) {
        dao.upsert(new BaseEntry<>(requestTime, id, Base64.getEncoder().encodeToString(body)));
        return new ResponseInfo(HttpURLConnection.HTTP_CREATED);
    }

    private ResponseInfo proceedDelete(String id, long requestTime) {
        dao.upsert(new BaseEntry<>(requestTime, id, null));
        return new ResponseInfo(HttpURLConnection.HTTP_ACCEPTED);
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }
}
