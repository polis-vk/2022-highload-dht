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
import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    private static final String INVALID_ID_MESSAGE = "Invalid id";
    private static final int NUMBER_VIRTUAL_NODES_PER_SHARD = 3;
    private static final int SELECTOR_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int DEFAULT_THREAD_POOL_SIZE = 32;
    private static final int KEEP_A_LIVE_TIME_IN_NANOSECONDS = 0;
    private static final int WORK_QUEUE_CAPACITY = 256;
    private static final String TIMESTAMP_HEADER = "Timestamp";
    private static final String TOMBSTONE_HEADER = "Tombstone";

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
            this.httpServer = createHttpServer(serviceConfig);
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
                httpServer = null;
                dao = null;
            } catch (IOException e) {
                throw new ServiceException("Error during DAO close", e);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public Response handleGetRequest(EntityParameters entityParameters) {
        if (!checkId(entityParameters.getId())) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        byte[] entry = dao.get(entityParameters.getId().getBytes(StandardCharsets.UTF_8));
        if (entry == null) {
            return ResponseEntity.notFound();
        }

        try {
            DaoEntry daoEntry = ObjectMapper.deserialize(entry);
            if (daoEntry.isTombstone()) {
                Response response = ResponseEntity.notFound();
                response.addHeader(TIMESTAMP_HEADER + daoEntry.getTimestamp());
                response.addHeader(TOMBSTONE_HEADER + daoEntry.isTombstone());
                return response;
            }

            Response response = ResponseEntity.ok(entry);
            response.addHeader(TIMESTAMP_HEADER + daoEntry.getTimestamp());
            return response;
        } catch (IOException | ClassNotFoundException e) {
            throw new ServiceException("Can not deserialize entry", e);
        }
    }

    public Response handlePutRequest(EntityParameters entityParameters, Request request) {
        if (!checkId(entityParameters.getId())) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        try {
            dao.put(entityParameters.getId().getBytes(StandardCharsets.UTF_8),
                    ObjectMapper.serialize(
                            new DaoEntry(
                                    entityParameters.getTimestamp(),
                                    request.getBody()
                            )
                    )
            );
            return ResponseEntity.created();
        } catch (IOException e) {
            throw new ServiceException("Can not serialize request body", e);
        }
    }

    public Response handleDeleteRequest(EntityParameters entityParameters) {
        if (!checkId(entityParameters.getId())) {
            return ResponseEntity.badRequest(INVALID_ID_MESSAGE);
        }

        try {
            dao.put(entityParameters.getId().getBytes(StandardCharsets.UTF_8),
                    ObjectMapper.serialize(
                            new DaoEntry(
                                    entityParameters.getTimestamp(),
                                    null,
                                    true
                            )
                    )
            );
            return ResponseEntity.accepted();
        } catch (IOException e) {
            throw new ServiceException("Can not serialize request body", e);
        }
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

    private HttpServer createHttpServer(ServiceConfig serviceConfig) {
        try {
            HttpServerConfig httpServerConfig = new HttpServerConfig();
            AcceptorConfig acceptor = new AcceptorConfig();

            acceptor.port = serviceConfig.selfPort();
            acceptor.reusePort = true;
            httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};
            httpServerConfig.selectors = SELECTOR_POOL_SIZE;

            return new Server(
                    httpServerConfig,
                    this,
                    createConsistentHash(serviceConfig),
                    new ThreadPoolExecutor(
                            DEFAULT_THREAD_POOL_SIZE,
                            DEFAULT_THREAD_POOL_SIZE,
                            KEEP_A_LIVE_TIME_IN_NANOSECONDS,
                            TimeUnit.NANOSECONDS,
                            new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY)
                    ),
                    new ScheduledThreadPoolExecutor(serviceConfig.clusterUrls().size())
            );
        } catch (IOException e) {
            throw new ServiceException("Can not create HttpServer", e);
        }
    }

    private static ConsistentHash<String> createConsistentHash(ServiceConfig serviceConfig) {
        List<Shard> shards = new ArrayList<>();
        for (int i = 0; i < serviceConfig.clusterUrls().size(); i++) {
            shards.add(new Shard(serviceConfig.clusterUrls().get(i), i));
        }

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

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
