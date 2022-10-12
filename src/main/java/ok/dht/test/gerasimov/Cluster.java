package ok.dht.test.gerasimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class Cluster {
    private static final Set<Integer> PORTS = Set.of(14454, 25565, 36676, 47787);
    private static final String LOCAL_HOST = "http://localhost:";
    private static final List<String> TOPOLOGY = PORTS.stream()
            .map(port -> LOCAL_HOST + port)
            .collect(Collectors.toList());
    private static final String PREFIX_TEMP_DIRECTORY = "TempDir-%s";

    private Cluster() {
    }

    public static void main(String[] args) {
        List<ServiceConfig> serviceConfigurations = PORTS.stream()
                .map(port -> {
                    try {
                        return new ServiceConfig(
                                port,
                                LOCAL_HOST,
                                TOPOLOGY,
                                Files.createTempDirectory(String.format(PREFIX_TEMP_DIRECTORY, port))
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();

        serviceConfigurations.forEach(serviceConfig -> {
            try {
                Service service = new ServiceImpl(serviceConfig);
                service.start().get(1, TimeUnit.SECONDS);
                Runtime.getRuntime().addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        service.stop();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                        )
                );
            } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
