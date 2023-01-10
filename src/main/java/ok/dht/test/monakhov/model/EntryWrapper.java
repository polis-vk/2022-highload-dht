package ok.dht.test.monakhov.model;

import com.google.common.primitives.SignedBytes;
import one.nio.http.Request;

import java.io.Serializable;
import java.sql.Timestamp;

public class EntryWrapper implements Comparable<EntryWrapper>, Serializable {
    public final byte[] bytes;
    public final Timestamp timestamp;
    public final boolean isTombstone;

    public EntryWrapper(Request request, Timestamp timestamp) {
        this.bytes = request.getBody();
        this.isTombstone = request.getMethod() == Request.METHOD_DELETE;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(EntryWrapper o) {
        int c = timestamp.compareTo(o.timestamp);
        if (c == 0) {
            if (isTombstone && o.isTombstone) {
                return 0;
            }
            if (isTombstone) {
                return 1;
            }
            if (o.isTombstone) {
                return -1;
            }
            return SignedBytes.lexicographicalComparator().compare(bytes, o.bytes);
        }
        return c;
    }
}
