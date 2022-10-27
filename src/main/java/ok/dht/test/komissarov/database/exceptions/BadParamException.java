package ok.dht.test.komissarov.database.exceptions;

public class BadParamException extends RuntimeException {

    public BadParamException(String message) {
        super(message);
    }

    public BadParamException(String message, Throwable cause) {
        super(message, cause);
    }
}
