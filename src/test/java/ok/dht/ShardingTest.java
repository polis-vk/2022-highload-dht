/*
 * Copyright 2021 (c) Odnoklassniki
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

import one.nio.http.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for a sharded two node {@link Service} cluster.
 *
 * @author Vadim Tsesko
 */
class ShardingTest extends TestBase {

    @ServiceTest(stage = 3, clusterSize = 2)
    void insert(List<ServiceInfo> serviceInfos) throws Exception {
        String key = "key";
        byte[] value = randomValue();

        for (ServiceInfo insertService : serviceInfos) {
            assertEquals(HttpURLConnection.HTTP_CREATED, insertService.upsert(key, value).statusCode());

            for (ServiceInfo getService : serviceInfos) {
                HttpResponse<byte[]> response = getService.get(key);
                assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                assertArrayEquals(value, response.body());
            }
        }
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void insertEmpty(List<ServiceInfo> serviceInfos) throws Exception {
        String key = randomId();
        byte[] value = new byte[0];

        for (ServiceInfo insertService : serviceInfos) {
            assertEquals(HttpURLConnection.HTTP_CREATED, insertService.upsert(key, value).statusCode());

            for (ServiceInfo getService : serviceInfos) {
                HttpResponse<byte[]> response = getService.get(key);
                assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                assertArrayEquals(value, response.body());
            }
        }
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void lifecycle2keys(List<ServiceInfo> serviceInfos) throws Exception {
        String key1 = randomId();
        byte[] value1 = randomValue();

        String key2 = randomId();
        byte[] value2 = randomValue();

        // Insert 1
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key1, value1).statusCode());

        // Check
        assertArrayEquals(value1, serviceInfos.get(0).get(key1).body());
        assertArrayEquals(value1, serviceInfos.get(1).get(key1).body());

        // Insert 2
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(1).upsert(key2, value2).statusCode());

        // Check
        assertArrayEquals(value1, serviceInfos.get(0).get(key1).body());
        assertArrayEquals(value1, serviceInfos.get(1).get(key1).body());
        assertArrayEquals(value2, serviceInfos.get(0).get(key2).body());
        assertArrayEquals(value2, serviceInfos.get(1).get(key2).body());

        // Delete 1
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, serviceInfos.get(0).delete(key1).statusCode());
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, serviceInfos.get(1).delete(key1).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(0).get(key1).statusCode());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(1).get(key1).statusCode());
        assertArrayEquals(value2, serviceInfos.get(0).get(key2).body());
        assertArrayEquals(value2, serviceInfos.get(1).get(key2).body());

        // Delete 2
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, serviceInfos.get(0).delete(key2).statusCode());
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, serviceInfos.get(1).delete(key2).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(0).get(key2).statusCode());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(1).get(key2).statusCode());
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void upsert(List<ServiceInfo> serviceInfos) throws Exception {
        String key = randomId();
        byte[] value1 = randomValue();
        byte[] value2 = randomValue();

        // Insert value1
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key, value1).statusCode());

        // Insert value2
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(1).upsert(key, value2).statusCode());

        // Check value 2
        for (ServiceInfo serviceInfo : serviceInfos) {
            HttpResponse<byte[]> response = serviceInfo.get(key);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(value2, response.body());
        }
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void upsertEmpty(List<ServiceInfo> serviceInfos) throws Exception {
        String key = randomId();
        byte[] value = randomValue();
        byte[] empty = new byte[0];

        // Insert value
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key, value).statusCode());

        // Insert empty
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key, empty).statusCode());

        // Check empty
        for (ServiceInfo serviceInfo : serviceInfos) {
            HttpResponse<byte[]> response = serviceInfo.get(key);
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertArrayEquals(empty, response.body());
        }
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void delete(List<ServiceInfo> serviceInfos) throws Exception {
        String key = randomId();
        byte[] value = randomValue();

        // Insert
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key, value).statusCode());
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(1).upsert(key, value).statusCode());

        // Delete
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, serviceInfos.get(0).delete(key).statusCode());

        // Check
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(0).get(key).statusCode());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, serviceInfos.get(1).get(key).statusCode());
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void distribute(List<ServiceInfo> serviceInfos) throws Exception {
        final String key = randomId();
        final byte[] value = randomValue();

        // Insert
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(0).upsert(key, value, 1, 1).statusCode());
        assertEquals(HttpURLConnection.HTTP_CREATED, serviceInfos.get(1).upsert(key, value, 1, 1).statusCode());

        // Stop all
        for (ServiceInfo serviceInfo : serviceInfos) {
            serviceInfo.stop();
        }

        int successCount = 0;
        // Check each
        for (ServiceInfo serviceInfo : serviceInfos) {
            serviceInfo.start();

            HttpResponse<byte[]> response = serviceInfo.get(key, 1, 1);
            if (response.statusCode() == HttpURLConnection.HTTP_OK && Arrays.equals(value, response.body())) {
                successCount++;
            }

            serviceInfo.stop();
        }

        assertEquals(1, successCount);
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void checkDistributionUniformity(List<ServiceInfo> serviceInfos) throws Exception {
        final int keysCount = 10_000;
        final float error = 0.01f;
        String key;
        final byte[] value = randomValue();

        for (int i = 0; i < keysCount; i++) {
            key = String.valueOf(i);
            serviceInfos.get(0).upsert(key, value);
        }

        float avgEntriesCountPerNode = 0;
        for (int i = 0; i < keysCount; i++) {
            if (serviceInfos.get(0).get(String.valueOf(i)).statusCode() == HttpURLConnection.HTTP_OK) {
                avgEntriesCountPerNode++;
            }
        }

        avgEntriesCountPerNode /= serviceInfos.size();

        serviceInfos.get(1).stop();

        int oneNodeEntriesCount = 0;
        for (int i = 0; i < keysCount; i++) {
            if (serviceInfos.get(0).get(String.valueOf(i)).statusCode() == HttpURLConnection.HTTP_OK) {
                oneNodeEntriesCount++;
            }
        }

        assertEquals(avgEntriesCountPerNode, oneNodeEntriesCount, keysCount * error);
    }

    @ServiceTest(stage = 3, clusterSize = 2)
    void checkDistributionUniformity2(List<ServiceInfo> serviceInfos) throws Exception {
        final int keysCount = 250_000;
        final int delta = 1;
        String key;
        final byte[] value = randomValue();

        for (int i = 0; i < keysCount; i++) {
            key = randomId();
            serviceInfos.get(0).upsert(key, value);
        }

        final int[] nodesFilesCount = new int[serviceInfos.size()];
        float avgFilesCountPerNode = 0;
        for (int i = 0; i < serviceInfos.size(); i++) {
            try (Stream<Path> files = Files.list(serviceInfos.get(i).getConfig().workingDir())) {
                nodesFilesCount[i] = (int) files.count();
            }
            avgFilesCountPerNode += nodesFilesCount[i];
        }

        avgFilesCountPerNode /= serviceInfos.size();

        for (int i = 0; i < serviceInfos.size(); i++) {
            assertEquals(avgFilesCountPerNode, nodesFilesCount[i], delta);
        }
    }

}
