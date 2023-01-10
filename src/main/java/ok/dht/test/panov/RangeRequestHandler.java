package ok.dht.test.panov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.panov.dao.Entry;
import ok.dht.test.panov.dao.lsm.MemorySegmentDao;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

public class RangeRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangeRequestHandler.class);

    private final MemorySegmentDao dao;
    private final ExecutorService executorService;

    public RangeRequestHandler(final MemorySegmentDao dao, final ExecutorService executorService) {
        this.dao = dao;
        this.executorService = executorService;
    }

    public void handleLocalRangeRequest(HttpSession session, String start, String end) {
        try {
            Response response = new Response(Response.OK);
            response.addHeader("Transfer-Encoding: chunked");
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.warn("Error during chunk sending");
        }

        MemorySegment startMS = MemorySegment.ofArray(start.getBytes(StandardCharsets.UTF_8));
        MemorySegment endMS = end == null ? null : MemorySegment.ofArray(end.getBytes(StandardCharsets.UTF_8));

        Iterator<Entry<MemorySegment>> it = dao.get(startMS, endMS);
        executorService.submit(() -> {
            try {
                ChunkedResponse.ChunkBuilder builder = new ChunkedResponse.ChunkBuilder();
                while (it.hasNext()) {
                    Entry<MemorySegment> element = it.next();
                    MemorySegment key = element.key();
                    MemorySegment value = element.value();

                    if (builder.isFull(key, value)) {
                        session.sendResponse(builder.build());
                        builder = new ChunkedResponse.ChunkBuilder();
                    }

                    builder.addElement(key, value);
                }
                if (builder.length() != 0) {
                    session.sendResponse(builder.build());
                }

                session.sendResponse(ChunkedResponse.END_CHUNK);
            } catch (IOException e) {
                LOGGER.warn("Error during chunk sending");
            }
        });
    }
}
