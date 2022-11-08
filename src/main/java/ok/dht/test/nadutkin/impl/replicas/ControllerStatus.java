package ok.dht.test.nadutkin.impl.replicas;

public class ControllerStatus {
    public Long timestamp;
    public byte[] answer;

    public ControllerStatus() {
        this.timestamp = -1L;
        this.answer = null;
    }

    public ControllerStatus(Long timestamp, byte[] answer) {
        this.timestamp = timestamp;
        this.answer = answer.clone();
    }
}
