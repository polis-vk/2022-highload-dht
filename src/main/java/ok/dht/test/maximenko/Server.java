package ok.dht.test.maximenko;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public final class Server {
    private Server() {

    }

    public static void main(String[] args) {
        final int firstPort = 19234;
        final int secondPort = 19235;
        final int thirdPort = 19236;

        final String url = "http://localhost:";
        final String firstUrl = url + firstPort;
        final String secondUrl = url + secondPort;
        final String thirdUrl = url + thirdPort;

        final List<String> clusterUrls = List.of(firstUrl, secondUrl, thirdUrl);
        final Logger logger = Logger.getLogger(String.valueOf(DatabaseService.class));
        String loggerMessage = "Server is listening on port: ";

        final ServiceConfig cfg1;
        final ServiceConfig cfg2;
        final ServiceConfig cfg3;
        try {
            cfg1 = new ServiceConfig(
                    firstPort,
                    firstUrl,
                    clusterUrls,
                    Files.createTempDirectory("server1")
            );

             cfg2 = new ServiceConfig(
                    secondPort,
                    secondUrl,
                    clusterUrls,
                    Files.createTempDirectory("server2")
            );

            cfg3 = new ServiceConfig(
                    thirdPort,
                    thirdUrl,
                    clusterUrls,
                    Files.createTempDirectory("server3")
            );
        } catch (IOException e) {
            logger.severe("Can't create tmp directories");
            return;
        }

        try {
            new DatabaseService(cfg1).start().get(1, TimeUnit.SECONDS);
            logger.info(loggerMessage + firstPort);
            new DatabaseService(cfg2).start().get(1, TimeUnit.SECONDS);
            logger.info(loggerMessage + secondPort);
            new DatabaseService(cfg3).start().get(1, TimeUnit.SECONDS);
            logger.info(loggerMessage + thirdPort);
        } catch (IOException e) {
            logger.severe("Database creation failure");
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.severe("Http creation future failure");
        }
    }
}
