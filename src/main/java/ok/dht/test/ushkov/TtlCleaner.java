package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.test.ushkov.dao.Entry;
import ok.dht.test.ushkov.dao.EntryIterator;
import ok.dht.test.ushkov.dao.RocksDBDao;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TtlCleaner {
    private RocksDBDao dao;
    private Service service;
    private ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private Logger log = LoggerFactory.getLogger(TtlCleaner.class);

    public TtlCleaner(RocksDBDao dao, Service service) {
        this.dao = dao;
    }

    public void start() {
        Runnable runnable = () -> {
            // TODO: stop handling requests
            try {
                service.stop().wait(30_000);
            } catch (IOException | InterruptedException e) {
                // TODO
            }

            long timestamp = System.currentTimeMillis();
            EntryIterator it = dao.range(new byte[0]);
            while (it.hasNext()) {
                Entry entry = it.next();
                if (entry.timestamp() + entry.ttl() < timestamp
                        || entry.value() == null) {
                    try {
                        dao.db.delete(entry.key());
                    } catch (RocksDBException e) {
                        log.error("Could not delete entry", e);
                    }
                }
            }

            try {
                service.start();
            } catch (IOException e) {
                // TODO
            }
        };
        cleaner.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.DAYS);
    }

    public void stop() throws InterruptedException {
        cleaner.shutdownNow();
        cleaner.awaitTermination(30, TimeUnit.SECONDS);
    }
}
