package ok.dht;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadRepairTest extends TestBase {

    // S0: k, v1
    // S1: k, v1
    // S2: k, v2
    // v1 is actual value
    // repair through S0
    @ServiceTest(stage = 7, clusterSize = 3)
    void repairSingle(List<ServiceInfo> nodes) throws Exception {
        String key = randomId();
        byte[] value1 = randomValue();
        byte[] value2 = randomValue();
        if (value1 == value2) {
            ++value2[0];
        }

        // fill third node
        nodes.get(0).stop();
        nodes.get(1).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(2).upsert(key, value2, 1, 3).statusCode());

        // fill two first nodes with other value
        nodes.get(0).start();
        nodes.get(1).start();
        nodes.get(2).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(0).upsert(key, value1, 2, 3).statusCode());

        // check that get query result is correct
        // value1 is more relevant than value2, because is most recent inserted
        nodes.get(2).start();
        {
            HttpResponse<byte[]> response = nodes.get(0).get(key, 2, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value1, response.body());

            // request for absent value, so that background repair has time to complete
            nodes.get(0).get(randomId(), 1, 3);
        }

        // check that repair is completed successfully and third server has value1
        nodes.get(0).stop();
        nodes.get(1).stop();
        {
            HttpResponse<byte[]> response = nodes.get(2).get(key, 1, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value1, response.body());
        }

        nodes.get(0).start();
        nodes.get(1).start();
    }

    // S0: k, v1
    // S1: k, v1
    // S2: k, v2
    // v2 is actual value
    // repair through S2
    @ServiceTest(stage = 7, clusterSize = 3)
    void repairMany(List<ServiceInfo> nodes) throws Exception {
        String key = randomId();
        byte[] value1 = randomValue();
        byte[] value2 = randomValue();
        if (value1 == value2) {
            ++value2[0];
        }

        // fill two first nodes
        nodes.get(2).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(0).upsert(key, value1, 2, 3).statusCode());

        // fill third node with other value
        nodes.get(2).start();
        nodes.get(0).stop();
        nodes.get(1).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(2).upsert(key, value2, 1, 3).statusCode());

        // check that get query result is correct
        // value2 is more relevant than value1, because is most recent inserted
        nodes.get(0).start();
        nodes.get(1).start();
        {
            HttpResponse<byte[]> response = nodes.get(2).get(key, 2, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());

            // request for absent value, so that background repair has time to complete
            nodes.get(2).get(randomId(), 3, 3);
        }

        // check that repair is completed successfully and first server has value2
        nodes.get(2).stop();
        nodes.get(1).stop();
        {
            HttpResponse<byte[]> response = nodes.get(0).get(key, 1, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());
        }

        // check that repair is completed successfully and second server has value2
        nodes.get(1).start();
        nodes.get(0).stop();
        {
            HttpResponse<byte[]> response = nodes.get(1).get(key, 1, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());
        }

        nodes.get(0).start();
        nodes.get(2).start();
    }

    // S0: k, v1
    // S1: k, v1
    // S2: k, v2
    // v2 is actual value
    // repair through S0
    @ServiceTest(stage = 7, clusterSize = 3)
    void repairSelf(List<ServiceInfo> nodes) throws Exception {
        String key = randomId();
        byte[] value1 = randomValue();
        byte[] value2 = randomValue();
        if (value1 == value2) {
            ++value2[0];
        }

        // fill two first nodes
        nodes.get(2).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(0).upsert(key, value1, 2, 3).statusCode());

        // fill third node with other value
        nodes.get(2).start();
        nodes.get(0).stop();
        nodes.get(1).stop();
        assertEquals(HttpURLConnection.HTTP_CREATED, nodes.get(2).upsert(key, value2, 1, 3).statusCode());

        // check that get query result is correct
        // value2 is more relevant than value1, because is most recent inserted
        nodes.get(0).start();
        nodes.get(1).start();
        {
            HttpResponse<byte[]> response = nodes.get(0).get(key, 2, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());

            // request for absent value, so that background repair has time to complete
            nodes.get(0).get(randomId(), 3, 3);
        }

        // check that repair is completed successfully and first server has value2
        nodes.get(2).stop();
        nodes.get(1).stop();
        {
            HttpResponse<byte[]> response = nodes.get(0).get(key, 1, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());
        }

        // check that repair is completed successfully and second server has value2
        nodes.get(1).start();
        nodes.get(0).stop();
        {
            HttpResponse<byte[]> response = nodes.get(1).get(key, 1, 3);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());
        }

        nodes.get(0).start();
        nodes.get(2).start();
    }
}
