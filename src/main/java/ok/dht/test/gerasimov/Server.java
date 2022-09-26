package ok.dht.test.gerasimov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.gerasimov.lsm.Config;
import ok.dht.test.gerasimov.lsm.Dao;
import ok.dht.test.gerasimov.lsm.Entry;
import ok.dht.test.gerasimov.lsm.artyomdrozdov.MemorySegmentDao;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Server {
    private static final int PORT = 8000;
    private static final String URL = "http://localhost:";

    private Server() {
    }

    public static void main(String[] args) {
        ServiceConfig serviceConfig = null;
        try {
            serviceConfig = new ServiceConfig(
                    PORT,
                    URL,
                    Collections.singletonList(URL),
                    Files.createTempDirectory("server")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Dao<MemorySegment, Entry<MemorySegment>> dao =
                    new MemorySegmentDao(new Config(serviceConfig.workingDir(), 2048))
        ) {
            ValidationService validationService = new ValidationService();

            ServiceImpl service = new ServiceImpl(serviceConfig, validationService);
            service.start().get(1, TimeUnit.SECONDS);
        } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
