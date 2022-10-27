package ok.dht.test.skroba;

import ok.dht.test.skroba.dao.MemorySegmentDao;
import ok.dht.test.skroba.dao.base.Config;
import one.nio.http.HttpServerConfig;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class MyServiceUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyServiceUtils.class);
    
    private MyServiceUtils() {
        // only pr const
    }
    
    static boolean isBadId(String id) {
        return id == null || id.isBlank();
    }
    
    static Response getEmptyResponse(String status) {
        return new Response(status, Response.EMPTY);
    }
    
    static Response getResponse(String status, String message) {
        return new Response(status, Utf8.toBytes(message));
    }
    
    static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
    
    static MemorySegmentDao createDaoFromDir(
            Path workingDir,
            int flushBytes
    ) throws IOException {
        Config configDao = new Config(workingDir, flushBytes);
        
        try {
            return new MemorySegmentDao(configDao);
        } catch (IOException err) {
            LOGGER.error("Error while creating database.\n" + err.getMessage());
            
            throw err;
        }
    }
}
