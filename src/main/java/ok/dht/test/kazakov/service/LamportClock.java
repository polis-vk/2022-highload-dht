package ok.dht.test.kazakov.service;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class LamportClock implements Closeable {

    public static final long INITIAL_VALUE = Long.MIN_VALUE;

    private final Path savePath;
    private final AtomicLong clock;

    // No need to close LamportClock here, resource management is taken care of on a higher level
    @SuppressWarnings("squid:S2095") // Use try-with-resources or close this "LamportClock" in a "finally" clause.
    public static LamportClock loadFrom(@Nonnull final Path savePath) throws IOException {
        final LamportClock result = new LamportClock(savePath);
        if (savePath.toFile().exists()) {
            final byte[] fileBytes = Files.readAllBytes(savePath);
            final long clockValue = DaoService.extractLongFromBytes(fileBytes, 0);
            result.clock.set(clockValue);
        }

        return result;
    }

    private LamportClock(@Nonnull final Path savePath) {
        this.clock = new AtomicLong(INITIAL_VALUE);
        this.savePath = savePath;
    }

    public long getValueToReceive(final long receivedTimestamp) {
        while (true) {
            final long oldClockValue = clock.get();
            final long newClockValue = Math.max(oldClockValue, receivedTimestamp) + 1;

            if (clock.compareAndSet(oldClockValue, newClockValue)) {
                return newClockValue;
            }
        }
    }

    public long getValueToSend() {
        return clock.incrementAndGet();
    }

    public long getValue() {
        return clock.get();
    }

    @Override
    public void close() throws IOException {
        final byte[] data = new byte[Long.BYTES];
        DaoService.putLongIntoBytes(data, 0, clock.get());
        Files.write(savePath, data);
    }
}
