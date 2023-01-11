package ok.dht.test.kuleshov;

public final class Validator {
    private Validator() {

    }

    public static boolean isCorrectId(String id) {
        return id != null && !id.isBlank();
    }

    public static boolean isCorrectAckFrom(int ack, int from, int clusters) {
        return ack > 0 && from > 0 && from <= clusters && ack <= from;
    }
}
