package ok.dht.kovalenko.dao;

import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.base.BaseEntry;
import ok.dht.kovalenko.dao.base.Entry;
import ok.dht.kovalenko.dao.base.ByteBufferDaoFactory;

import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class DaoFiller {

    private static final CopyOnWriteArrayList<ExecutorService> executors = new CopyOnWriteArrayList<>();

    private DaoFiller() {
    }

    public static void fillDao(LSMDao dao, ByteBufferDaoFactory daoFactory, int nEntries) throws Exception {
        List<TypedEntry> entries = entries(nEntries, daoFactory);
//        for (int i = 0; i < entries.size(); ++i) {
//            dao.upsert(entries.get(i));
//            if (i % 10_000 == 0) {
//                Thread.sleep(100);
//            }
//        }
        entries.forEach(dao::upsert);
        //runInParallel(10, nEntries, value -> dao.upsert(entries.get(value))).close();
    }

    private static List<TypedEntry> entries(int count, ByteBufferDaoFactory daoFactory) {
        return entries("k", "v", count, daoFactory);
    }

    private static List<TypedEntry> entries(String keyPrefix, String valuePrefix, int count,
                                            ByteBufferDaoFactory daoFactory) {
        return new AbstractList<>() {
            @Override
            public TypedEntry get(int index) {
                checkInterrupted();
                if (index >= count || index < 0) {
                    throw new IndexOutOfBoundsException("Index is " + index + ", size is " + count);
                }
                //String paddedIdx = String.format("%010d", index);
                ByteBuffer key = daoFactory.fromString(keyPrefix + index);
                ByteBuffer value = daoFactory.fromString(valuePrefix + index);
                return new TypedBaseEntry(key, value);
            }

            @Override
            public int size() {
                return count;
            }
        };
    }

    public static Entry<String> entryAt(int index) {
        return new BaseEntry<>(keyAt(index), valueAt(index));
    }

    public static String keyAt(int index) {
        return keyAt("k", index);
    }

    public static String valueAt(int index) {
        return valueAt("v", index);
    }

    public static String keyAt(String prefix, int index) {
        String paddedIdx = String.format("%010d", index);
        return prefix + paddedIdx;
    }

    public static String valueAt(String prefix, int index) {
        String paddedIdx = String.format("%010d", index);
        return prefix + paddedIdx;
    }

    private static void checkInterrupted() {
        if (Thread.interrupted()) {
            throw new RuntimeException(new InterruptedException());
        }
    }

    private static AutoCloseable runInParallel(int threadCount, int tasksCount, ParallelTask runnable) {
        ExecutorService service = Executors.newFixedThreadPool(threadCount);
        executors.add(service);
        try {
            AtomicInteger index = new AtomicInteger();
            List<Future<Void>> futures = service.invokeAll(Collections.nCopies(threadCount, () -> {
                while (!Thread.interrupted()) {
                    int i = index.getAndIncrement();
                    if (i >= tasksCount) {
                        return null;
                    }
                    runnable.run(i);
                }
                throw new InterruptedException("Execution is interrupted");
            }));
            return () -> {
                for (Future<Void> future : futures) {
                    future.get();
                }
            };
        } catch (InterruptedException | OutOfMemoryError e) {
            throw new RuntimeException(e);
        }
    }

    private interface ParallelTask {
        void run(int taskIndex) throws Exception;
    }
}
