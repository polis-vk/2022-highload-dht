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

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Contains utility methods for unit tests.
 *
 * @author Vadim Tsesko
 */
public abstract class TestBase {

    protected static final int KEY_LENGTH = 16;
    private static final int VALUE_LENGTH = 1024;

    protected final HttpClient client = HttpClient.newHttpClient();

    protected static String randomId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    private static byte[] randomBytes(final int length) {
        Assertions.assertTrue(length > 0);
        final byte[] result = new byte[length];
        ThreadLocalRandom.current().nextBytes(result);
        return result;
    }

    protected void waitForVersionAdvancement() throws Exception {
        long ms = System.currentTimeMillis();
        while (ms == System.currentTimeMillis()) {
            Thread.sleep(1);
        }
    }

    protected static byte[] randomValue() {
        return randomBytes(VALUE_LENGTH);
    }

    protected static byte[] randomKey() {
        return randomBytes(KEY_LENGTH);
    }

    protected static String endpoint(final int port) {
        return "http://localhost:" + port;
    }
}
