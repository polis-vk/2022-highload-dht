package ok.dht.test.kovalenko.dao.base;

import ok.dht.ServiceConfig;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestDaoB<D, E extends TimedEntry<D>> implements Dao<String, TimedEntry<String>> {

    public final ServiceConfig config;
    final DaoFactoryB.Factory<D, E> factory;
    final String name;
    final List<Closeable> children = new ArrayList<>();
    Dao<D, E> delegate;

    public TestDaoB(DaoFactoryB.Factory<D, E> factory, ServiceConfig config) throws IOException {
        this.factory = factory;
        this.config = config;
        this.delegate = factory.createDao(config);

        Class<?> delegateClass = delegate.getClass();
        String packageName = delegateClass.getPackageName();
        String lastPackagePart = packageName.substring(packageName.lastIndexOf('.') + 1);

        this.name = "TestDao<" + lastPackagePart + "." + delegateClass.getSimpleName() + ">";
    }

    public Dao<String, TimedEntry<String>> reopen() throws IOException {
        if (delegate != null) {
            throw new IllegalStateException("Reopening open db");
        }
        TestDaoB<D, E> child = new TestDaoB<>(factory, config);
        children.add(child);
        return child;
    }

    @Override
    public TimedEntry<String> get(String key) throws IOException {
        E result = delegate.get(factory.fromString(key));
        if (result == null) {
            return null;
        }
        return new BaseTimedEntry<>(
                result.timestamp(),
                factory.toString(result.key()),
                factory.toString(result.value())
        );
    }

    @Override
    public Iterator<TimedEntry<String>> get(String from, String to) throws IOException {
        Iterator<E> iterator = delegate.get(
                factory.fromString(from),
                factory.fromString(to)
        );
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public TimedEntry<String> next() {
                E next = iterator.next();
                long timestamp = next.timestamp();
                String key = factory.toString(next.key());
                String value = factory.toString(next.value());
                return new BaseTimedEntry<>(timestamp, key, value);
            }
        };
    }

    @Override
    public void upsert(TimedEntry<String> timedEntry) {
        BaseTimedEntry<D> e = new BaseTimedEntry<>(
                System.currentTimeMillis(),
                factory.fromString(timedEntry.key()),
                factory.fromString(timedEntry.value())
        );
        delegate.upsert(factory.fromBaseEntry(e));
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void compact() throws IOException {
        delegate.compact();
    }

    @Override
    public void close() throws IOException {
        for (Closeable child : children) {
            child.close();
        }
        children.clear();
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
