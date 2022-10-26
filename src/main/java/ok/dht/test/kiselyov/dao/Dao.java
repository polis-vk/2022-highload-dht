package ok.dht.test.kiselyov.dao;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

public interface Dao<D, E, B extends Entry<D, E>> extends Closeable {

    /**
     * Returns ordered iterator of entries with keys between from (inclusive) and to (exclusive).
     *
     * @param from lower bound of range (inclusive)
     * @param to   upper bound of range (exclusive)
     * @return entries [from;to)
     */
    Iterator<B> get(D from, D to) throws IOException;

    /**
     * Returns entry by key. Note: default implementation is far from optimal.
     *
     * @param key entry`s key
     * @return entry
     */
    default Entry<D, E> get(D key) throws IOException {
        Iterator<B> iterator = get(key, null);
        if (!iterator.hasNext()) {
            return null;
        }
        Entry<D, E> next = iterator.next();
        if (next.key().equals(key)) {
            return next;
        }
        return null;
    }

    /**
     * Returns ordered iterator of all entries with keys from (inclusive).
     * @param from lower bound of range (inclusive)
     * @return entries with key >= from
     */
    default Iterator<B> allFrom(D from) throws IOException {
        return get(from, null);
    }

    /**
     * Returns ordered iterator of all entries with keys < to.
     * @param to upper bound of range (exclusive)
     * @return entries with key < to
     */
    default Iterator<B> allTo(D to) throws IOException {
        return get(null, to);
    }

    /**
     * Returns ordered iterator of all entries.
     * @return all entries
     */
    default Iterator<B> all() throws IOException {
        return get(null, null);
    }

    /**
     * Inserts of replaces entry.
     * @param entry element to upsert
     */
    void upsert(B entry);

    /**
     * Persists data (no-op by default).
     */
    default void flush() throws IOException {
        // Do nothing
    }

    /**
     * Compacts data (no-op by default).
     */
    default void compact() throws IOException {
        // Do nothing
    }

    /**
     * Releases Dao (calls flush by default).
     */
    @Override
    default void close() throws IOException {
        flush();
    }

}
