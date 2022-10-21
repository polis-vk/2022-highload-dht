package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.LoadBalancer;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import ok.dht.test.kovalenko.MyServiceBase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public static void fillDaos(Map<String, MyServiceBase> urlsServices, int idxEntryFrom, int idxEntryTo)
            throws InterruptedException, IOException {
        int sleepTreshold = 10_000 * urlsServices.size();
        List<String> urls = urlsServices.keySet().stream().toList();
        for (int i = idxEntryFrom; i <= idxEntryTo; ++i) {
            if (i % sleepTreshold == 0) {
                Thread.sleep(100);
            }
            TypedEntry entry = entryAt(i);
            String curServiceUrl = LoadBalancer.nextNodeUrl(daoFactory.toString(entry.key()), urls);
            urlsServices.get(curServiceUrl).getDao().upsert(entry);
        }
        for (Map.Entry<String, MyServiceBase> service : urlsServices.entrySet()) {
            service.getValue().getDao().flush();
        }
    }

    private static TypedBaseEntry entryAt(int idx) {
        return new TypedBaseEntry(daoFactory.fromString("k" + idx), daoFactory.fromString("v" + idx));
    }
}
