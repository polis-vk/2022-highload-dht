package ok.dht.test.skroba;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public final class ServerLocalImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLocalImpl.class);
    private static final int MAX_COUNT_OF_CLUSTERS = 6;
    
    private ServerLocalImpl() {
        //No public implementation.
    }
    
    public static void main(String[] args) {
        if (!validateArguments(args)) {
            return;
        }
        
        final ServiceConfig config = getServiceConfig(args);
        
        final Service service = new ServiceImpl(config);
        
        try {
            service.start()
                    .get();
        } catch (IOException | InterruptedException | ExecutionException e) {
            LOGGER.error("Exception while starting service: " + e.getMessage());
            return;
        }
        
        LOGGER.info("Service has started on: " + config.selfUrl());
    }
    
    private static boolean validateArguments(final String[] args) {
        if (args == null || args.length == 0 || Arrays.stream(args)
                .anyMatch(Objects::isNull) || !checkIfNumber(args[0])) {
            LOGGER.warn("Wrong arguments!");
            LOGGER.warn("Program arguments should be: <n - count of clusters> <1. cluster port> ... <n. " +
                    "cluster's port>");
            return false;
        }
        
        final int countOfClusters = Integer.parseInt(args[0]);
        
        if (countOfClusters < 1 || countOfClusters > MAX_COUNT_OF_CLUSTERS) {
            LOGGER.warn("Count of clusters must be positive number less than: " + MAX_COUNT_OF_CLUSTERS);
            return false;
        }
        
        if (args.length != countOfClusters + 1) {
            LOGGER.warn("Wrong amount of ports in args!");
            LOGGER.warn("Got: " + (args.length - 1));
            LOGGER.warn("Should be: " + countOfClusters);
            return false;
        }
        
        for (int i = 1; i < args.length; i++) {
            if (!checkIfValidPort(args[i])) {
                LOGGER.warn("Invalid port on position: " + i + ", value: " + args[i]);
                return false;
            }
        }
        
        return true;
    }
    
    private static ServiceConfig getServiceConfig(final String[] args) {
        final String portStr = args[1];
        final String directory = "serverdb_" + portStr;
        final int port = Integer.parseInt(portStr);
        
        try {
            return new ServiceConfig(
                    port,
                    getUrlFromPort(portStr),
                    Arrays.stream(args)
                            .skip(1)
                            .map(ServerLocalImpl::getUrlFromPort)
                            .toList(),
                    Files.createTempDirectory(directory));
        } catch (IOException e) {
            LOGGER.error("Can't create DB directory: " + directory);
            throw new RuntimeException(e.getMessage());
        }
    }
    
    private static boolean checkIfNumber(final String arg) {
        for (char ch : arg.toCharArray()) {
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean checkIfValidPort(final String arg) {
        return arg.length() == 4 && arg.charAt(0) != '0' && checkIfNumber(arg);
    }
    
    private static String getUrlFromPort(final String port) {
        return "http://localhost:" + port;
    }
}
