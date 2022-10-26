package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;

import java.io.IOException;

public final class DaoFiller {

    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();

    private DaoFiller() {
    }

    public static void fillDao(LSMDao dao, int idxEntryFrom, int idxEntryTo) throws InterruptedException, IOException {
        int sleepTreshold = 10_000;
        for (int i = idxEntryFrom; i <= idxEntryTo; ++i) {
            if (i % sleepTreshold == 0) {
                Thread.sleep(50);
            }
            dao.upsert(entryAt(i));
        }
        dao.flush();
    }

    private static TypedBaseEntry entryAt(int idx) {
        return new TypedBaseEntry(daoFactory.fromString("k" + idx), daoFactory.fromString("v" + idx));
    }
}
