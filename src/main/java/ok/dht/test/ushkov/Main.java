package ok.dht.test.ushkov;

import ok.dht.ServiceConfig;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int[] ports = new int[]{1337, 1338, 1339, 1340};
        List<String> urls = Arrays.stream(ports)
                .mapToObj(p -> "http://localhost:" + p)
                .toList();
        for (int port : ports) {
            String url = "http://localhost:" + port;
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    urls,
                    Files.createTempDirectory("server")
            );
            RocksDBService db = new RocksDBService(cfg);
            db.start().get(1, TimeUnit.SECONDS);
//            for (int i = 0; i < 2_000_000; i++) {
//                var buffer = ByteBuffer.allocate(Long.BYTES + 1 + 100);
//                buffer.putLong(System.currentTimeMillis());
//                buffer.put((byte) 0);
//                buffer.put(("value_" + i).getBytes(StandardCharsets.UTF_8));
//                db.db.put(
//                        ("key_" + i).getBytes(StandardCharsets.UTF_8),
//                        buffer.array()
//                );
//            }
            System.out.println("Socket is ready: " + url);
        }
    }
}
