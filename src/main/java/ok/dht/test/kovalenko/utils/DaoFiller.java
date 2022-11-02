package ok.dht.test.kovalenko.utils;

import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.LoadBalancer;
import ok.dht.test.kovalenko.MyServiceBase;
import ok.dht.test.kovalenko.Node;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class DaoFiller {

    public static final DaoFiller INSTANSE = new DaoFiller();
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
        LoadBalancer loadBalancer = new LoadBalancer();
        loadBalancer.add((MyServiceBase) urlsServices.values().toArray()[0]);
        for (int i = idxEntryFrom; i <= idxEntryTo; ++i) {
            if (i % sleepTreshold == 0) {
                Thread.sleep(100);
            }
            TypedTimedEntry entry = entryAt(i);
            Node responsibleNodeForKey = loadBalancer.responsibleNodeForKey(daoFactory.toString(entry.key()));
            urlsServices.get(responsibleNodeForKey.selfUrl()).getDao().upsert(entry);
        }
        for (Map.Entry<String, MyServiceBase> service : urlsServices.entrySet()) {
            service.getValue().getDao().flush();
        }
    }

    private static TypedBaseTimedEntry entryAt(int idx) {
        return new TypedBaseTimedEntry(
                System.currentTimeMillis(),
                daoFactory.fromString(Integer.toString(idx)),
                daoFactory.fromString(Integer.toString(idx))
        );
    }
}
