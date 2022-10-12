package ok.dht.test.kurdyukov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kurdyukov.service.ServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainNode3 {

    private MainNode3() {

    }

    public static void main(String[] args) throws IOException {

        Service service1 = new ServiceImpl.Factory().create(
                new ServiceConfig(
                        4244,
                        "http://localhost:4244",
                        List.of("http://localhost:4242", "http://localhost:4243", "http://localhost:4244"),
                        Path.of("/Users/kurdyukov-kir/data3")
                )
        );


        try {
            service1.start().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
