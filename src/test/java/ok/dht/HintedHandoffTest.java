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

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for hinted handoff.
 *
 * @author Nikita Siniachenko
 */
class HintedHandoffTest extends TestBase {

    private static byte[] chunkOf(
            String key,
            String value) {
        return (key + '\n' + value).getBytes();
    }

    @ServiceTest(clusterSize = 2, stage = 7, bonusForWeek = 7)
    void oneFailedReplicaOneKey(List<ServiceInfo> nodes) throws Exception {
        nodes.get(0).stop();

        String key = randomId();
        System.out.println("KEY is " + key);
        byte[] value = randomValue();
        for (int i = 0; i < value.length; i++) {
            if (value[i] == (byte) '\n') {
                value[i] = (byte) '\r';
            }
        }
        nodes.get(1).upsert(key, value, 1, 2);

        System.out.println("HEREEEEEEEE");
        nodes.get(0).start();
        nodes.get(1).stop();
        HttpResponse<byte[]> response = nodes.get(0).get(key, 1, 2);
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        byte[] receivedValue = response.body();

        assertArrayEquals(value, receivedValue);
    }
}
