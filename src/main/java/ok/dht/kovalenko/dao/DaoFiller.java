package ok.dht.kovalenko.dao;

import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.base.ByteBufferDaoFactory;

import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.List;

public final class DaoFiller {

    private DaoFiller() {
    }

    public static void fillDao(LSMDao dao, ByteBufferDaoFactory daoFactory, int numEntries) {
        List<TypedEntry> entries = entries(numEntries, daoFactory);
        entries.forEach(dao::upsert);
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

    private static void checkInterrupted() {
        if (Thread.interrupted()) {
            throw new RuntimeException(new InterruptedException());
        }
    }
}
