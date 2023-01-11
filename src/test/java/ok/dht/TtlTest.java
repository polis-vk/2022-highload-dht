package ok.dht;

import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TtlTest extends TestBase {
    @ServiceTest(stage = 7)
    void nonExpiredTtl(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value, 5000).statusCode());
        assertArrayEquals(value, service.get(key).body());
    }

    @ServiceTest(stage = 7)
    void expiredTtl(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value, 500).statusCode());
        Thread.sleep(1000);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key).statusCode());
    }

    @ServiceTest(stage = 7, clusterSize = 3)
    void nonExpiredTtlReplicated(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value, 2, 3,5000).statusCode());
        assertArrayEquals(value, service.get(key, 2, 3).body());
    }

    @ServiceTest(stage = 7, clusterSize = 3)
    void expiredTtlReplicated(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value, 2, 3,500).statusCode());
        Thread.sleep(1000);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key, 2, 3).statusCode());
    }
}
