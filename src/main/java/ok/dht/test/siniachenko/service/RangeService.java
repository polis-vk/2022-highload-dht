package ok.dht.test.siniachenko.service;

import one.nio.http.Request;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

public class RangeService {
    private static final Logger LOG = LoggerFactory.getLogger(RangeService.class);

    private final DB levelDb;

    public RangeService(DB levelDb) {
        this.levelDb = levelDb;
    }

    public EntityStreamQueueItem handleRange(Request request) {
        String start = request.getParameter("start");
        String end = request.getParameter("end");
        DBIterator iterator = levelDb.iterator();
        Iterator<Map.Entry<byte[], byte[]>> inRangeIterator = new InRangeEntityIterator(
            iterator,
            start,
            end
        );
        return new EntityStreamQueueItem(inRangeIterator);
    }
}
