package ok.dht.test.shakhov.dao;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
