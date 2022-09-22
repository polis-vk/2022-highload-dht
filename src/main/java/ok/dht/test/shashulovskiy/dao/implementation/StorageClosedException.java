package ok.dht.test.shashulovskiy.dao.implementation;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
