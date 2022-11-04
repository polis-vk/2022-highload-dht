package ok.dht.test.kazakov.service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ExceptionUtils {
    private ExceptionUtils() {
        // no operations
    }

    public static Exception addSuppressed(@Nullable final Exception original,
                                          @Nonnull final Exception suppressed) {
        if (original == null) {
            return suppressed;
        }

        original.addSuppressed(suppressed);
        return original;
    }

    public static Exception tryExecute(@Nullable final Exception previousException,
                                       @Nonnull final ThrowingRunnable throwingRunnable) {
        try {
            throwingRunnable.run();
        } catch (final Exception e) {
            return addSuppressed(previousException, e);
        }

        return previousException;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        // throwing `Exception` to catch all possible exceptions in tryExecute
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        void run() throws Exception;
    }
}
