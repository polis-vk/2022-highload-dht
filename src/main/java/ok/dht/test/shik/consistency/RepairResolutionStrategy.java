package ok.dht.test.shik.consistency;

public interface RepairResolutionStrategy extends InconsistencyResolutionStrategy {

    @Override
    default boolean shouldRepair() {
        return true;
    }
}
