/*
 * Copyright HttpURLConnection.HTTP_ACCEPTED1 (c) Odnoklassniki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ok.dht;

import java.net.HttpURLConnection;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for single node {@link ServiceInfo} API.
 *
 * @author Vadim Tsesko
 */
class SingleNodeTest extends TestBase {

    @ServiceTest(stage = 1)
    void emptyKey(ServiceInfo service) throws Exception {
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, service.get("").statusCode());
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, service.delete("").statusCode());
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, service.upsert("", new byte[]{0}).statusCode());
    }

    @ServiceTest(stage = 1)
    void absentParameterRequest(ServiceInfo service) throws Exception {
        assertEquals(
                HttpURLConnection.HTTP_BAD_REQUEST,
                client.send(
                        service.request("/v0/entity").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                ).statusCode()
        );
        assertEquals(
                HttpURLConnection.HTTP_BAD_REQUEST,
                client.send(
                        service.request("/v0/entity").PUT(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                ).statusCode()
        );
        assertEquals(
                HttpURLConnection.HTTP_BAD_REQUEST,
                client.send(
                        service.request("/v0/entity").DELETE().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                ).statusCode()
        );
    }

    @ServiceTest(stage = 1)
    void badRequest(ServiceInfo service) throws Exception {
        assertEquals(
                HttpURLConnection.HTTP_BAD_REQUEST,
                client.send(
                        service.request("/abracadabra").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                ).statusCode()
        );
    }

    @ServiceTest(stage = 1)
    void getAbsent(ServiceInfo service) throws Exception {
        assertEquals(
                HttpURLConnection.HTTP_NOT_FOUND,
                service.get("absent").statusCode()
        );
    }

    @ServiceTest(stage = 1)
    void deleteAbsent(ServiceInfo service) throws Exception {
        assertEquals(
                HttpURLConnection.HTTP_ACCEPTED,
                service.delete("absent").statusCode()
        );
    }

    @ServiceTest(stage = 1)
    void insert(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        // Insert
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value).statusCode());

        // Check
        HttpResponse<byte[]> response = service.get(key);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        assertArrayEquals(value, response.body());
    }

    @ServiceTest(stage = 1)
    void insertEmpty(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = new byte[0];

        // Insert
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value).statusCode());

        // Check
        HttpResponse<byte[]> response = service.get(key);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        assertArrayEquals(value, response.body());
    }

    @ServiceTest(stage = 1)
    void lifecycle2keys(ServiceInfo service) throws Exception {
        String key1 = randomId();
        byte[] value1 = randomValue();
        String key2 = randomId();
        byte[] value2 = randomValue();

        // Insert 1
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key1, value1).statusCode());

        // Check
        assertArrayEquals(value1, service.get(key1).body());

        // Insert 2
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key2, value2).statusCode());

        // Check
        assertArrayEquals(value1, service.get(key1).body());
        assertArrayEquals(value2, service.get(key2).body());

        // Delete 1
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, service.delete(key1).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key1).statusCode());
        assertArrayEquals(value2, service.get(key2).body());

        // Delete 2
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, service.delete(key2).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key2).statusCode());
    }

    @ServiceTest(stage = 1)
    void upsert(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value1 = randomValue();
        byte[] value2 = randomValue();

        // Insert value1
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value1).statusCode());

        // Insert value2
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value2).statusCode());

        // Check value 2
        HttpResponse<byte[]> response = service.get(key);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        assertArrayEquals(value2, response.body());
    }

    @ServiceTest(stage = 1, bonusForWeek = 1)
    void respectFileFolder(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        // Insert value
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value).statusCode());

        // Check value
        HttpResponse<byte[]> response = service.get(key);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        assertArrayEquals(value, response.body());

        // Remove data and recreate
        service.cleanUp();
        service.start();

        // Check absent data
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key).statusCode());
    }

    @ServiceTest(stage = 1)
    void upsertEmpty(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();
        byte[] empty = new byte[0];

        // Insert value
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value).statusCode());

        // Insert empty
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, empty).statusCode());

        // Check empty
        HttpResponse<byte[]> response = service.get(key);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        assertArrayEquals(empty, response.body());
    }

    @ServiceTest(stage = 1)
    void delete(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        // Insert
        assertEquals(HttpURLConnection.HTTP_CREATED, service.upsert(key, value).statusCode());

        // Delete
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, service.delete(key).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, service.get(key).statusCode());
    }

    @ServiceTest(stage = 1)
    void post(ServiceInfo service) throws Exception {
        String key = randomId();
        byte[] value = randomValue();
        assertEquals(
                HttpURLConnection.HTTP_BAD_METHOD,
                service.post(key, value).statusCode()
        );
    }
}
