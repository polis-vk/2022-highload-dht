package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

abstract public class HttpAsyncService {

    protected static final Logger logger = LoggerFactory.getLogger(HttpAsyncService.class);

    protected final DaoRepository daoRepository;

    public HttpAsyncService(DaoRepository daoRepository) {
        this.daoRepository = daoRepository;
    }

    public void executeOnRequest(Request request, HttpSession session) {
        try {
            handleRequest(request, session);
        } catch (IOException e) {
            logger.error("Send response is fail.", e);
            session.close();
        }
    }

    protected abstract void handleRequest(Request request, HttpSession session) throws IOException;

    protected static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }
}
