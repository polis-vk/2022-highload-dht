package ok.dht.test.kosnitskiy.dao;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
