package ok.dht.test.kuleshov.utils;

public final class RequestsUtils {
    private RequestsUtils() {

    }

    public static Integer parseInt(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
