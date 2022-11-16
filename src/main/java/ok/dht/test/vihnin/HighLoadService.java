package ok.dht.test.vihnin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.vihnin.database.DataBase;
import ok.dht.test.vihnin.database.DataBaseRocksDBImpl;
import one.nio.http.HttpServer;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class HighLoadService implements Service {
    private final ServiceConfig config;
    private HttpServer server;
    private DataBase<String, byte[]> storage;

    private ResponseManager responseManager;

    public HighLoadService(ServiceConfig config) {
        this.config = config;
    }

    private static DataBase<String, byte[]> getDataStorage(ServiceConfig config) {
        DataBase<String, byte[]> storage;
        try {
            storage = new DataBaseRocksDBImpl(config.workingDir());
        } catch (RocksDBException e) {
            e.printStackTrace();
            storage = null;
        }
        return storage;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        storage = getDataStorage(this.config);
        responseManager = new ResponseManager(storage);
        server = new ParallelHttpServer(config, responseManager);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        if (storage != null) {
            storage.close();
        }
        storage = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new HighLoadService(config);
        }
    }

}
