package ok.dht.test.komissarov;

import ok.dht.ServiceConfig;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CourseServer {

    private CourseServer() {
        // Only main method
    }

    public static void main(String[] args) throws Exception {
        int[] ports = new int[]{19234, 19235, 19236};

        for (int port : ports) {
            String url = "http://localhost:" + port;
            ServiceConfig cfg = new ServiceConfig(
                    port,
                    url,
                    List.of("http://localhost:19234",
                            "http://localhost:19235",
                            "http://localhost:19236"),
                    Files.createTempDirectory("server")
            );
            new CourseService(cfg).start().get(1, TimeUnit.SECONDS);
        }
    }
}
