package ok.dht.test.galeev;

import com.google.common.base.Objects;
import ok.dht.ServiceConfig;
import ok.dht.test.galeev.dao.DaoMiddleLayer;
import ok.dht.test.galeev.dao.entry.BaseEntry;
import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.galeev.dao.utils.DaoConfig;
import ok.dht.test.galeev.dao.utils.StringTimestampByteConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Node {
    private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);
    private static final Duration REQUEST_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);
    public static final int FATAL_ERROR_AMOUNT = 7;
    public final AtomicInteger errorCount;
    public final String nodeAddress;
    public volatile boolean isAlive;

    public Node(String nodeAddress) {
        this.errorCount = new AtomicInteger(0);
        this.nodeAddress = nodeAddress;
        this.isAlive = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node node)) return false;
        return nodeAddress.equals(node.nodeAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nodeAddress);
    }

    // First value is for cancellation
    // If null was returned -> some error
    // If (null,null) -> Not Found
    // If (timestamp, null) -> tombstone
    // If (timestamp, value) -> everything is OK
    public abstract CompletableFuture<Entry<Timestamp, byte[]>> get(String key);

    // If true -> everything is OK
    // If false -> smth went wrong
    public abstract CompletableFuture<Boolean> put(String key, Timestamp timestamp, byte[] body);

    // If true -> everything is OK
    // If false -> smth went wrong
    public abstract CompletableFuture<Boolean> delete(String key, Timestamp timestamp);

    public static class LocalNode extends Node {
        public static final int FLUSH_THRESHOLD_BYTES = 16777216; // 16MB
        private final DaoMiddleLayer<String, Entry<Timestamp, byte[]>> dao;

        public LocalNode(ServiceConfig config) throws IOException {
            super(config.selfUrl());
            dao = createDao(config);
        }

        public CompletableFuture<Iterator<Entry<String, Entry<Timestamp, byte[]>>>> get(String from, String to) {
            return CompletableFuture.completedFuture(dao.get(from, to));
        }

        @Override
        public CompletableFuture<Entry<Timestamp, byte[]>> get(String key) {
            Entry<Timestamp, byte[]> entry = getFromDao(key);
            return CompletableFuture.completedFuture(entry);
        }

        public Entry<Timestamp, byte[]> getFromDao(String key) {
            Entry<String, Entry<Timestamp, byte[]>> entry = dao.get(key);
            if (entry == null) {
                return new BaseEntry<>(null, null);
            } else {
                return entry.value();
            }
        }

        @Override
        public CompletableFuture<Boolean> put(String key, Timestamp timestamp, byte[] body) {
            putToDao(key, timestamp, body);
            return CompletableFuture.completedFuture(true);
        }

        public void putToDao(String key, Timestamp timestamp, byte[] body) {
            putToDao(key, new BaseEntry<>(timestamp, body));
        }

        public void putToDao(String key, Entry<Timestamp, byte[]> entry) {
            dao.upsert(key, entry);
        }

        @Override
        public CompletableFuture<Boolean> delete(String key, Timestamp timestamp) {
            deleteFromDao(key, timestamp);
            return CompletableFuture.completedFuture(true);
        }

        public void deleteFromDao(String key, Timestamp timestamp) {
            dao.upsert(new BaseEntry<>(key, new BaseEntry<>(timestamp, null)));
        }

        public void stop() throws IOException {
            dao.stop();
        }

        private static DaoMiddleLayer<String, Entry<Timestamp, byte[]>> createDao(ServiceConfig config)
                throws IOException {
            if (!Files.exists(config.workingDir())) {
                Files.createDirectory(config.workingDir());
            }
            return new DaoMiddleLayer<>(
                    new DaoConfig(
                            config.workingDir(),
                            FLUSH_THRESHOLD_BYTES //16MB
                    ),
                    new StringTimestampByteConverter()
            );
        }
    }

    public static class ClusterNode extends Node {

        private final HttpClient httpClient;

        public ClusterNode(String nodeAddress, HttpClient httpClient) {
            super(nodeAddress);
            this.httpClient = httpClient;
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        @Override
        public CompletableFuture<Entry<Timestamp, byte[]>> get(String key) {
            if (!isAlive) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<HttpResponse<byte[]>> sendAsyncFuture = httpClient.sendAsync(
                    requestBuilderForKey(nodeAddress, key).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            CompletableFuture<Entry<Timestamp, byte[]>> returnFuture = sendAsyncFuture.thenApply((response) -> {
                if (response.statusCode() == 200) {
                    return getEntryFromByteArray(response.body());
                } else if (response.statusCode() == 404) {
                    return new BaseEntry<Timestamp, byte[]>(null, null);
                } else {
                    return null;
                }
            }).exceptionally((e) -> {
                if (errorCount.incrementAndGet() > FATAL_ERROR_AMOUNT) {
                    isAlive = false;
                }
                LOGGER.debug(String.format("%nExceptionally sending GET to Node %s with key: %s%n",
                        nodeAddress,
                        key
                ), e);
                return null;
            });
            returnFuture.whenComplete((entry, throwable) -> {
                if (!sendAsyncFuture.isDone()) {
                    LOGGER.debug("Canceling useless GET requests by key: " + key);
                    sendAsyncFuture.cancel(false);
                }
            });
            return returnFuture;
        }

        @Override
        public CompletableFuture<Boolean> put(String key, Timestamp timestamp, byte[] body) {
            if (!isAlive) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            return httpClient.sendAsync(
                    requestBuilderForKey(nodeAddress, key).PUT(
                            HttpRequest.BodyPublishers.ofByteArray(entryToByteArray(timestamp, body))
                    ).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            ).thenApply((response) -> {
                if (response.statusCode() == 201) {
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }).exceptionally((e) -> {
                if (errorCount.incrementAndGet() > FATAL_ERROR_AMOUNT) {
                    isAlive = false;
                }
                LOGGER.debug(String.format("%nExceptionally sending PUT to Node %s with key: %s and timestamp: %s%n",
                        nodeAddress,
                        key,
                        timestamp.getTime()
                ), e);
                return Boolean.FALSE;
            });
        }

        @Override
        public CompletableFuture<Boolean> delete(String key, Timestamp timestamp) {
            if (!isAlive) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            return httpClient.sendAsync(
                    requestBuilderForDeleteKey(nodeAddress, key, timestamp).PUT(
                            HttpRequest.BodyPublishers.ofByteArray(entryToByteArray(timestamp, null))
                    ).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            ).thenApply((response) -> {
                if (response.statusCode() == 202) {
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }).exceptionally((e) -> {
                if (errorCount.incrementAndGet() > FATAL_ERROR_AMOUNT) {
                    isAlive = false;
                }
                LOGGER.debug(String.format("%nExceptionally sending DELETE to Node %s with key: %s and timestamp: %s%n",
                        nodeAddress,
                        key,
                        timestamp.getTime()
                ), e);
                return Boolean.FALSE;
            });
        }

        public static Entry<Timestamp, byte[]> getEntryFromByteArray(byte[] body) {
            ByteBuffer bf = ByteBuffer.wrap(body);
            Timestamp timestamp = new Timestamp(bf.getLong());
            int bodyLength = bf.getInt();
            if (bodyLength == -1) {
                return new BaseEntry<>(timestamp, null);
            }
            byte[] value;
            value = new byte[bodyLength];
            bf.get(value);
            return new BaseEntry<>(timestamp, value);
        }

        public static byte[] entryToByteArray(Timestamp timestamp, @Nullable byte[] body) {
            if (body == null) {
                return ByteBuffer.allocate(Long.BYTES + Integer.BYTES)
                        .putLong(timestamp.getTime())
                        .putInt(-1)
                        .flip()
                        .array();
            } else {
                return ByteBuffer.allocate(Long.BYTES + Integer.BYTES + body.length)
                        .putLong(timestamp.getTime())
                        .putInt(body.length)
                        .put(body, 0, body.length)
                        .flip()
                        .array();
            }
        }

        private static HttpRequest.Builder requestBuilder(String uri, String path) {
            return HttpRequest.newBuilder(URI.create(uri + path)).timeout(REQUEST_TIMEOUT);
        }

        private static HttpRequest.Builder requestBuilderForKey(String uri, String key) {
            return requestBuilder(uri, "/v0/local/entity?id=" + key);
        }

        private static HttpRequest.Builder requestBuilderForDeleteKey(String uri, String key, Timestamp timestamp) {
            return requestBuilder(uri, "/v0/local/entity?id=" + key + "&timestamp=" + timestamp.getTime());
        }
    }
}
