package ok.dht.test.gerasimov.exception;

/**
 * @author Michael Gerasimov
 */
public class ServiceException extends RuntimeException {
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
