package ok.dht.test.ilin.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ilin.config.ConsistentHashingConfig;
import ok.dht.test.ilin.config.ExpandableHttpServerConfig;
import ok.dht.test.ilin.domain.Entity;
import ok.dht.test.ilin.domain.Headers;
import ok.dht.test.ilin.domain.Serializer;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import ok.dht.test.ilin.replica.ReplicasHandler;
import ok.dht.test.ilin.servers.ExpandableHttpServer;
import ok.dht.test.ilin.sharding.ShardHandler;
import ok.dht.test.ilin.utils.TimestampUtils;
import one.nio.http.HttpServer;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class EntityService implements Service {
    private final ServiceConfig config;
    private HttpServer server;
    private RocksDB rocksDB;
    private final Logger logger = LoggerFactory.getLogger(EntityService.class);

    public EntityService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        try {
            this.rocksDB = RocksDB.open(options, config.workingDir().toString());
        } catch (RocksDBException ex) {
            logger.error("Error initializing RocksDB");
            throw new IOException(ex);
        }
        ConsistentHashingConfig consistentHashingConfig = new ConsistentHashingConfig();
        ConsistentHashing consistentHashing = new ConsistentHashing(config.clusterUrls(), consistentHashingConfig);
        ShardHandler shardHandler = new ShardHandler(config.selfUrl(), consistentHashing);
        ReplicasHandler replicasHandler = new ReplicasHandler(config.selfUrl(), this, consistentHashing, shardHandler);
        server = new ExpandableHttpServer(
            replicasHandler,
            config.clusterUrls().size(),
            createConfigFromPort(config.selfPort())
        );
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        rocksDB.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    public Response getEntity(String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            byte[] result = rocksDB.get(id.getBytes(StandardCharsets.UTF_8));
            if (result == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            Entity entity = Serializer.deserializeEntity(result);
            if (entity == null) {
                logger.error("Failed to deserialize entity.");
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
            if (entity.tombstone()) {
                Response response = new Response(Response.NOT_FOUND, entity.data());
                response.addHeader(Headers.TOMBSTONE_HEADER);
                response.addHeader(Headers.TIMESTAMP_HEADER + entity.timestamp());
                return response;
            }
            Response response = new Response(Response.OK, entity.data());
            response.addHeader(Headers.TIMESTAMP_HEADER + entity.timestamp());
            return response;
        } catch (RocksDBException e) {
            logger.error("Error get entry in RocksDB");
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to deserialize entity: {}", e.getMessage());
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response upsertEntity(String id, Request request) {
        byte[] body = request.getBody();
        if (id.isEmpty() || body == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            rocksDB.put(
                id.getBytes(StandardCharsets.UTF_8),
                Serializer.serializeEntity(new Entity(TimestampUtils.extractTimestamp(request), false, body))
            );
        } catch (RocksDBException e) {
            logger.error("Error saving entry in RocksDB");
        } catch (IOException e) {
            logger.error("Failed to serialize entity: {}", e.getMessage());
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(String id, Request request) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            rocksDB.put(
                id.getBytes(StandardCharsets.UTF_8),
                Serializer.serializeEntity(new Entity(TimestampUtils.extractTimestamp(request), true, new byte[0]))
            );
        } catch (RocksDBException e) {
            logger.error("Error delete entry in RocksDB");
        } catch (IOException e) {
            logger.error("Failed to serialize entity: {}", e.getMessage());
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static ExpandableHttpServerConfig createConfigFromPort(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        ExpandableHttpServerConfig httpConfig = new ExpandableHttpServerConfig();
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.selectors = 2;
        return httpConfig;
    }

    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new EntityService(config);
        }
    }
}
