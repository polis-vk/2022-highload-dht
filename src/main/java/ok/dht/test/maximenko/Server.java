package ok.dht.test.maximenko;

import ok.dht.ServiceConfig;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Server {
    private Server() {

    }
    public static void main(String[] args) throws Exception {
        int firstPort = 19234;
        int secondPort = 19235;
        int thirdPort = 19236;

        String url = "http://localhost:";
        String firstUrl  = url + firstPort;
        String secondUrl = url + secondPort;
        String thirdUrl  = url + thirdPort;

        List<String> clusterUrls = List.of(firstUrl, secondUrl, thirdUrl);

        ServiceConfig cfg1 = new ServiceConfig(
                firstPort,
                firstUrl,
                clusterUrls,
                Files.createTempDirectory("server1")
        );

        ServiceConfig cfg2 = new ServiceConfig(
                secondPort,
                secondUrl,
                clusterUrls,
                Files.createTempDirectory("server2")
        );

        ServiceConfig cfg3 = new ServiceConfig(
                thirdPort,
                thirdUrl,
                clusterUrls,
                Files.createTempDirectory("server3")
        );
        new DatabaseService(cfg1).start().get(1, TimeUnit.SECONDS);
        System.out.println("Server is listening on port: " + firstPort);
        new DatabaseService(cfg2).start().get(1, TimeUnit.SECONDS);
        System.out.println("Server is listening on port: " + secondPort);
        new DatabaseService(cfg3).start().get(1, TimeUnit.SECONDS);
        System.out.println("Server is listening on port: " + thirdPort);
    }
}
