package ok.dht.test.gerasimov;

public class EntityParameters {
    private String id;
    private Integer ack;
    private Integer from;
    private Long timestamp;
    private boolean tombstone;

    public EntityParameters(String id) {
        this.id = id;
        this.tombstone = false;
    }

    public EntityParameters(String id, Integer ack, Integer from) {
        this.id = id;
        this.ack = ack;
        this.from = from;
        this.tombstone = false;
    }

    public String getId() {
        return id;
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public boolean isTombstone() {
        return tombstone;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setAck(Integer ack) {
        this.ack = ack;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTombstone(boolean tombstone) {
        this.tombstone = tombstone;
    }
}
