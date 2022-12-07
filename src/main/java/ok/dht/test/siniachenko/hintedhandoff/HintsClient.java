package ok.dht.test.siniachenko.hintedhandoff;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.Utils;
import org.iq80.leveldb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static ok.dht.test.siniachenko.TycoonHttpServer.REPLICA_URL_HEADER;

public class HintsClient {
    private static final Logger LOG = LoggerFactory.getLogger(HintsClient.class);
    // TODO choose size
    public static final int BUFFER_CAPACITY = 255 * 1024;

    private final ServiceConfig config;
    private final DB levelDb;
    private final HttpClient httpClient;
    private final ExecutorService executorService;

    public HintsClient(ServiceConfig config, DB levelDb, HttpClient httpClient, ExecutorService executorService) {
        this.config = config;
        this.levelDb = levelDb;
        this.httpClient = httpClient;
        this.executorService = executorService;
    }

    public void fetchHintsFromReplica(String replicaUrl, CountDownLatch countDownLatch) {
        httpClient.sendAsync(
                HttpRequest.newBuilder()
                    .GET()
                    .header(REPLICA_URL_HEADER, config.selfUrl())
                    .uri(URI.create(replicaUrl + TycoonHttpServer.HINTS_PATH))
                    .build(),
                HttpResponse.BodyHandlers.buffering(HttpResponse.BodyHandlers.ofInputStream(), BUFFER_CAPACITY)
            ).thenAcceptAsync(response -> {
                countDownLatch.countDown();
                if (response.statusCode() == 200) {
                    InputStream body = response.body();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
                    try {
                        processBody(body, byteBuffer);
                    } catch (IOException e) {
                        LOG.error("IO exception receiving hints from " + replicaUrl, e);
                    } catch (RuntimeException e) {
                        LOG.error("Runtime exception receiving hints from " + replicaUrl, e);
                        throw e;
                    }
                }
            }, executorService)
            .exceptionallyAsync(throwable -> {
                countDownLatch.countDown();
                LOG.error("Timeout or Error receiving hints from " + replicaUrl);
                return null;
            }, executorService);
    }

    private void processBody(InputStream body, ByteBuffer byteBuffer) throws IOException {
        while (true) {
            int read = body.read(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.remaining());
            if (read < 0) {
                if (byteBuffer.position() > 0) {
                    processHintInBuffer(byteBuffer);
                }
                break;
            }
            byteBuffer.position(byteBuffer.position() + read);
            if (!byteBuffer.hasRemaining()) {
                processHintInBuffer(byteBuffer);
            }
        }
    }

    private void processHintInBuffer(ByteBuffer byteBuffer) {
        Hint hint = readHint(byteBuffer);
        if (hint == null) {
            throw new RuntimeException("cannot parse hint from stream");
        }
        byte[] storedValue = levelDb.get(hint.key);
        if (
            storedValue == null
                || Utils.readTimeMillisFromBytes(storedValue)
                < Utils.readTimeMillisFromBytes(hint.value)
        ) {
            levelDb.put(hint.key, hint.value);
        }
        int hintLength = hint.key.length + hint.value.length + 2;
        System.arraycopy(byteBuffer.array(), hintLength, byteBuffer.array(), 0, byteBuffer.position() - hintLength);
        byteBuffer.position(byteBuffer.position() - hintLength);
    }

    private static Hint readHint(ByteBuffer byteBuffer) {
        byte[] array = byteBuffer.array();
        Hint hint = new Hint();
        int keyLength = 0;
        boolean keyRead = false;
        for (int i = 9; i < byteBuffer.capacity(); i++) {
            if (keyRead && isSeparator(array, i)) {
                hint.value = new byte[i - keyLength - 10];
                System.arraycopy(array, keyLength + 1, hint.value, 0, hint.value.length);
                return hint;
            } else if (!keyRead && array[i] == '\n') {
                keyLength = i;
                hint.key = new byte[keyLength];
                System.arraycopy(array, 0, hint.key, 0, hint.key.length);
                keyRead = true;
            }
        }
        // TODO: handle
        return null;
    }

    private static boolean isSeparator(byte[] array, int index) {
        for (int i = index - 9; i <= index; i++) {
            if (array[i] != '\n') {
                return false;
            }
        }
        return true;
    }
}
