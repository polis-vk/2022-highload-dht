package ok.dht.test.gerasimov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.gerasimov.Factory;
import ok.dht.test.gerasimov.client.CircuitBreakerClient;
import ok.dht.test.gerasimov.exception.EntityServiceException;
import ok.dht.test.gerasimov.exception.ServerException;
import one.nio.http.HttpServer;
import org.iq80.leveldb.DB;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author Michael Gerasimov
 */
public class StartStopService implements Service {
    private final ServiceConfig serviceConfig;

    private boolean isClosed = true;
    private HttpServer httpServer;
    private DB dao;

    public StartStopService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            this.dao = Factory.createDao(serviceConfig.workingDir());

            HandleService entityService = new EntityService(
                    dao,
                    Factory.createConsistentHash(serviceConfig),
                    serviceConfig.selfPort(),
                    new CircuitBreakerClient()
            );

            this.httpServer = Factory.createHttpServer(
                    serviceConfig,
                    Map.of(entityService.getEndpoint(), entityService)
            );

            httpServer.start();
            isClosed = false;
        } catch (IOException e) {
            throw new ServerException("DAO can not be created", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        if (!isClosed) {
            try {
                httpServer.stop();
                dao.close();
                isClosed = true;
                httpServer = null;
                dao = null;
            } catch (IOException e) {
                throw new EntityServiceException("Error during DAO close", e);
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}
