package ok.dht.test.shik.workers;

import java.util.concurrent.TimeUnit;

public class WorkersConfig {

    private int corePoolSize;
    private int maxPoolSize = Integer.MAX_VALUE;
    private long keepAliveTime;
    private TimeUnit unit = TimeUnit.MILLISECONDS;
    private QueuePolicy queuePolicy;
    private int queueCapacity = 100;

    public enum QueuePolicy {
        LIFO,
        FIFO,
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public QueuePolicy getQueuePolicy() {
        return queuePolicy;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public static class Builder {

        private final WorkersConfig config;

        public Builder() {
            config = new WorkersConfig();
        }

        public Builder corePoolSize(int corePoolSize) {
            config.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            config.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder keepAliveTime(long keepAliveTime) {
            config.keepAliveTime = keepAliveTime;
            return this;
        }

        public Builder unit(TimeUnit unit) {
            config.unit = unit;
            return this;
        }

        public Builder queuePolicy(QueuePolicy queuePolicy) {
            config.queuePolicy = queuePolicy;
            return this;
        }

        public Builder queueCapacity(int queueCapacity) {
            config.queueCapacity = queueCapacity;
            return this;
        }

        public WorkersConfig build() {
            return config;
        }
    }
}
