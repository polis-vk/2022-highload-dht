package ok.dht.test.kurdyukov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kurdyukov.service.ServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class MainNode1 {

    private MainNode1() {

    }

    public static void main(String[] args) throws IOException {
        Service service = new ServiceImpl.Factory().create(
                new ServiceConfig(
                        4242,
                        "http://localhost:4242",
                        List.of("http://localhost:4242", "http://localhost:4243", "http://localhost:4244"),
                        Path.of("/Users/kurdyukov-kir/data1")
                )
        );
        try {
            service.start().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
