package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class HttpRangeService extends HttpAsyncService {
    private static final Set<Integer> supportMethods = Set
            .of(Request.METHOD_GET);

    public HttpRangeService(DaoRepository daoRepository) {
        super(daoRepository);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!supportMethods.contains(request.getMethod())) {
            session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
            return;
        }

        String start = request.getParameter("start=");
        String end = request.getParameter("end=");

        if (start == null
                || end == null
                || start.isBlank()
                || end.isBlank()
                || start.compareTo(end) > 0
        ) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        DBIterator iterator = daoRepository.iterator();

        iterator.seek(end.getBytes(StandardCharsets.UTF_8));
        session.sendResponse(
                new HttpChunkedResponse(
                        Response.OK,
                        iterator,
                        bytes(start)
                )
        );
    }
}
