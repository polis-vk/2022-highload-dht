package ok.dht.test.galeev;

import one.nio.http.Request;

public class Header {
    public static final String FROM_PARAMETR = "from=";
    public static final String ID_PARAMETR = "id=";
    public static final String ACK_PARAMETR = "ack=";

    private final String key;
    private final int ack;
    private final int from;
    private final boolean isOk;

    public Header(Request request, int amountOfPhysicalNodes) {
        String tmpKey;
        int tmpAck;
        int tmpFrom;
        boolean tmpIsOk = true;
        tmpKey = request.getParameter(ID_PARAMETR);
        if (tmpKey == null || tmpKey.isEmpty()) {
            tmpIsOk = false;
        }
        String ackString = request.getParameter(ACK_PARAMETR);
        String fromString = request.getParameter(FROM_PARAMETR);
        try {
            tmpFrom = (fromString == null) ? amountOfPhysicalNodes
                    : Integer.parseInt(fromString);
            tmpAck = (ackString == null) ? tmpFrom / 2 + 1 : Integer.parseInt(ackString);
        } catch (NumberFormatException e) {
            tmpAck = -1;
            tmpFrom = -1;
            tmpIsOk = false;
        }
        if (tmpAck > tmpFrom || tmpAck <= 0) {
            tmpAck = -1;
            tmpFrom = -1;
            tmpIsOk = false;
        }
        key = tmpKey;
        ack = tmpAck;
        from = tmpFrom;
        isOk = tmpIsOk;
    }

    public boolean isOk() {
        return isOk;
    }

    public String getKey() {
        return key;
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }
}
