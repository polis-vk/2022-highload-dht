package ok.dht.test.kurdyukov;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    public static void main(String[] args) throws IOException {
        Service service = new ServiceImpl.Factory().create(
                new ServiceConfig(
                        4242,
                        "http://localhost",
                        List.of("http://localhost"),
                        Files.createTempDirectory("server")
                )
        );

        try {
            service.start().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("Fail start service.");;
        }
    }
}
