package ok.dht.test.gerasimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.exception.ServiceException;
import one.nio.http.HttpServer;
import one.nio.http.Request;
import one.nio.http.Response;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    private static final String INVALID_ID_MESSAGE = "Invalid id";

    private final ServiceConfig serviceConfig;

    private HttpServer httpServer;
    private DB dao;

    public ServiceImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            this.httpServer = new Server(serviceConfig.selfPort(), this);
            this.dao = createDao(serviceConfig.workingDir());
            httpServer.start();
        } catch (IOException e) {
            throw new ServerException("DAO can not be created", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        try {
            httpServer.stop();
            dao.close();
        } catch (IOException e) {
            throw new ServerException("Error during DAO close", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    public Response handleGetRequest(String id) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        byte[] entry = dao.get(id.getBytes(StandardCharsets.UTF_8));
        if (entry == null) {
            return ResponseEntity.notFound();
        }

        return ResponseEntity.ok(entry);
    }

    public Response handlePutRequest(String id, Request request) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        dao.put(id.getBytes(StandardCharsets.UTF_8), request.getBody());
        return ResponseEntity.created();
    }

    public Response handleDeleteRequest(String id) {
        if (!checkId(id)) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        dao.delete(id.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.accepted();
    }

    private static DB createDao(Path path) throws IOException {
        try {
            return factory.open(new File(path.toString()), new Options());
        } catch (IOException e) {
            throw new ServiceException("Can not create DAO", e);
        }
    }

    private static boolean checkId(String id) {
        return !id.isBlank() && id.chars().noneMatch(Character::isWhitespace);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
