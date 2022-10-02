package ok.dht.test.ushkov;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8000;
        String url = "http://localhost:" + port;
        ServiceConfig cfg = new ServiceConfig(
                port,
                url,
                Collections.singletonList(url),
                Files.createTempDirectory("server")
        );
        RocksDBService service = new RocksDBService(cfg);
        service.start().get(1, TimeUnit.SECONDS);

//        for (int i = 0; i < 2_000_000; i++) {
//            service.db.put(
//                    Integer.toString(i).getBytes(StandardCharsets.UTF_8),
//                    Integer.toString(i).repeat(100).getBytes(StandardCharsets.UTF_8)
//            );
//        }

//        service.db.flush(new FlushOptions());

        System.out.println("Socket is ready: " + url);
    }
}
