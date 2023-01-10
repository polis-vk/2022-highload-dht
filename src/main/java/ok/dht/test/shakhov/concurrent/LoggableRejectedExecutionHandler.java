package ok.dht.test.shakhov.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class LoggableRejectedExecutionHandler implements RejectedExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(LoggableRejectedExecutionHandler.class);

    private final String executorName;

    public LoggableRejectedExecutionHandler(String executorName) {
        this.executorName = executorName;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.warn("Task was rejected by {} executor", executorName);
    }
}
