package ok.dht.test.gerasimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.exception.ServiceException;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.ConsistentHashImpl;
import ok.dht.test.gerasimov.sharding.Shard;
import ok.dht.test.gerasimov.sharding.VNode;
import one.nio.http.HttpServer;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    private static final String INVALID_ID_MESSAGE = "Invalid id";
    private static final int NUMBER_VIRTUAL_NODES_PER_SHARD = 3;

    private final ServiceConfig serviceConfig;

    private boolean isClosed = true;
    private HttpServer httpServer;
    private DB dao;

    public ServiceImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;

    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            this.httpServer = new Server(serviceConfig.selfPort(), this, createConsistentHash(serviceConfig));
            this.dao = createDao(serviceConfig.workingDir());
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
            } catch (IOException e) {
                throw new ServerException("Error during DAO close", e);
            }
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

    public Response handleAdminRequest() {
        try {
            DBIterator iterator = dao.iterator();
            iterator.seekToFirst();
            int count = 0;
            while (iterator.hasNext()) {
                count++;
                iterator.next();
            }
            iterator.close();
            return ResponseEntity.ok(
                    String.format(
                            "Server name: %s%s. Count: %d",
                            serviceConfig.selfUrl(),
                            serviceConfig.selfPort(),
                            count
                    )
            );
        } catch (IOException e) {
            return ResponseEntity.internalError("Can not create iterator");
        }
    }

    private static boolean checkId(String id) {
        return !id.isBlank() && id.chars().noneMatch(Character::isWhitespace);
    }

    private static DB createDao(Path path) throws IOException {
        try {
            return factory.open(new File(path.toString()), new Options());
        } catch (IOException e) {
            throw new ServiceException("Can not create DAO", e);
        }
    }

    private static ConsistentHash<String> createConsistentHash(ServiceConfig serviceConfig) {
        List<Shard> shards = serviceConfig.clusterUrls().stream().map(Shard::new).toList();
        List<VNode> vnodes = new ArrayList<>();
        for (Shard shard : shards) {
            for (int i = 0; i < NUMBER_VIRTUAL_NODES_PER_SHARD; i++) {
                int hashcode = Hash.murmur3(shard.getHost() + shard.getPort() + i);
                vnodes.add(new VNode(shard, hashcode));
            }
        }
        vnodes.sort(VNode::compareTo);
        return new ConsistentHashImpl<>(vnodes, shards);
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
