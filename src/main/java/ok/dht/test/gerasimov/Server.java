package ok.dht.test.gerasimov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.gerasimov.lsm.Config;
import ok.dht.test.gerasimov.lsm.Dao;
import ok.dht.test.gerasimov.lsm.Entry;
import ok.dht.test.gerasimov.lsm.artyomdrozdov.MemorySegmentDao;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class Server {
    private static final int PORT = 25565;
    private static final String URL = "http://localhost:";

    public Server() {

    }

    public static void main(String[] args) throws Exception {
        ServiceConfig serviceConfig = new ServiceConfig(
                PORT,
                URL,
                Collections.singletonList(URL),
                Files.createTempDirectory("server")
        );

        try (Dao<MemorySegment, Entry<MemorySegment>> dao =
                    new MemorySegmentDao(new Config(serviceConfig.workingDir(), 2048))
        ) {
            ValidationService validationService = new ValidationService();

            ServiceImpl service = new ServiceImpl(serviceConfig, validationService);
            service.start().get(1, TimeUnit.SECONDS);
        }
    }
}
