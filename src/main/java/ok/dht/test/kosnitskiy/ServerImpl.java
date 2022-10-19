package ok.dht.test.kosnitskiy;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerImpl {

    private ServerImpl() {
        // Only main method
    }

    public static void main(String[] args) throws IOException,
            ExecutionException,
            InterruptedException,
            TimeoutException {
        int amount = Integer.parseInt(args[0]);
        int place = Integer.parseInt(args[1]);
        int basePort = 19230;
        int thisPort = basePort + place;
        List<String> clusterUrls = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            clusterUrls.add("http://localhost:" + (basePort + i));
        }
        String url = "http://localhost:" + thisPort;
        ServiceConfig cfg = new ServiceConfig(
                thisPort,
                url,
                clusterUrls,
                Path.of("/home/sanerin/GitHub/data_files/data" + place + "/")
        );
        ServiceImpl service = new ServiceImpl(cfg);
        service.start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
