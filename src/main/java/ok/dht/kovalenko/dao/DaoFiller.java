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

    public static void fillDao(LSMDao dao, ByteBufferDaoFactory daoFactory, int numEntries)
            throws InterruptedException {
        var entries = entries(numEntries, daoFactory);
        for (int i = 0; i < entries.size(); ++i) {
            if (i % 10_000 == 0) {
                Thread.sleep(100);
            }
            dao.upsert(entries.get(i));
        }
    }

    private static List<TypedEntry> entries(int count, ByteBufferDaoFactory daoFactory) {
        return entries("k", "v", count, daoFactory);
    }

    private static List<TypedEntry> entries(String keyPrefix, String valuePrefix, int count,
                                            ByteBufferDaoFactory daoFactory) {
        return entries(keyPrefix, valuePrefix, 1, count, daoFactory);
    }

    private static List<TypedEntry> entries(String keyPrefix, String valuePrefix, int idxFrom, int idxTo,
                                            ByteBufferDaoFactory daoFactory) {
        return new AbstractList<>() {
            @Override
            public TypedEntry get(int index) {
                ByteBuffer key = daoFactory.fromString(keyPrefix + (index + idxFrom));
                ByteBuffer value = daoFactory.fromString(valuePrefix + (index + idxFrom));
                return new TypedBaseEntry(key, value);
            }

            @Override
            public int size() {
                return idxTo - idxFrom + 1;
            }
        };
    }
}
