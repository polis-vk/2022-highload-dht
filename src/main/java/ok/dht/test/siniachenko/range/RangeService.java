package ok.dht.test.siniachenko.range;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import java.util.Iterator;
import java.util.Map;

public class RangeService {

    private final DB levelDb;
    private final ChunkedTransferEncoder chunkedTransferEncoder;

    public RangeService(DB levelDb, ChunkedTransferEncoder chunkedTransferEncoder) {
        this.levelDb = levelDb;
        this.chunkedTransferEncoder = chunkedTransferEncoder;
    }

    public EntityChunkStreamQueueItem handleRange(String start, String end) {
        DBIterator iterator = levelDb.iterator();
        iterator.seekToFirst();
        Iterator<Map.Entry<byte[], byte[]>> inRangeIterator = new InRangeEntityIterator(
            iterator,
            start,
            end
        );
        return chunkedTransferEncoder.encodeEntityChunkStream(
            inRangeIterator, false, false
        );
    }
}
