package ok.dht.test.skroba.db.exception;

import java.io.IOException;

public class DaoException extends IOException {
    public DaoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
