package ok.dht.test.nadutkin.impl.replicas;

import ok.dht.test.nadutkin.impl.utils.UtilsClass;

public class ControllerStatus {
    public Long timestamp;
    public byte[] answer;

    public ControllerStatus() {
        this.timestamp = -1L;
        this.answer = null;
    }

    public ControllerStatus(Long timestamp, byte[] answer) {
        this.timestamp = timestamp;
        this.answer = UtilsClass.processBytes(answer);
    }
}
