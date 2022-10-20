package ok.dht.test.kurdyukov;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class Main {

    private Main() {

    }

    public static void main(String[] args) throws IOException {
        Service service = new ServiceImpl.Factory().create(
                new ServiceConfig(
                        4242,
                        "http://localhost",
                        List.of("http://localhost"),
                        Path.of("/Users/kurdyukov-kir/data")
                )
        );

        try {
            service.start().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
