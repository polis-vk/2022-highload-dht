# Concurrency тест для MostRelevantResponseHandler

```java
package ok.dht.test.kazakov.service.ws;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.IIII_Result;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public class MostRelevantResponseHandlerStressTest {

    final int needAcknowledgements;
    final int totalRequests;

    final AtomicInteger expectedSuccessfulResponses = new AtomicInteger(0);

    final AtomicReference<Integer> bestResponsePriority = new AtomicReference<>(null);
    final AtomicInteger successfulResponses = new AtomicInteger(0);
    final AtomicInteger totalResponses = new AtomicInteger(0);

    final AtomicInteger successfulResponsesToClient = new AtomicInteger(0);
    final AtomicInteger errorResponsesToClient = new AtomicInteger(0);

    public MostRelevantResponseHandlerStressTest(final int needAcknowledgements, final int totalRequests) {
        this.needAcknowledgements = needAcknowledgements;
        this.totalRequests = totalRequests;
    }

    // adapted copy-paste of MostRelevantResponseHandler logic
    protected void addResponse(final int responsePriority, final boolean isResponseSuccessful) {
        if (isResponseSuccessful) {
            Integer bestResponsePriority = this.bestResponsePriority.get();
            while (bestResponsePriority == null || responsePriority > bestResponsePriority) {
                if (this.bestResponsePriority.compareAndSet(bestResponsePriority, responsePriority)) {
                    break;
                }
                bestResponsePriority = this.bestResponsePriority.get();
            }

            if (successfulResponses.incrementAndGet() == needAcknowledgements) {
                successfulResponsesToClient.incrementAndGet();
            }
        }

        if (totalResponses.incrementAndGet() == totalRequests && successfulResponses.get() < needAcknowledgements) {
            errorResponsesToClient.incrementAndGet();
        }
    }

    protected void updateResult(final IIII_Result result) {
        result.r1 = successfulResponses.get() == expectedSuccessfulResponses.get() ? 1 : 0;
        result.r2 = totalResponses.get() == totalRequests ? 1 : 0;
        result.r3 = successfulResponsesToClient.get() + errorResponsesToClient.get();
        result.r4 = expectedSuccessfulResponses.get() >= needAcknowledgements
                ? successfulResponsesToClient.get()
                : errorResponsesToClient.get();
    }

    protected boolean initializeActor() {
        final boolean responseIsSuccessfulForActor = ThreadLocalRandom.current().nextBoolean();
        if (responseIsSuccessfulForActor) {
            expectedSuccessfulResponses.incrementAndGet();
        }
        return responseIsSuccessfulForActor;
    }

    @JCStressTest
    @Outcome(id = "1, 1, 1, 1", expect = ACCEPTABLE)
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @State
    public static class ThreeAcksFourRequests extends MostRelevantResponseHandlerStressTest {

        public ThreeAcksFourRequests() {
            super(3, 4);
        }

        @Actor
        public void actor1() {
            addResponse(1, initializeActor());
        }

        @Actor
        public void actor2() {
            addResponse(2, initializeActor());
        }

        @Actor
        public void actor3() {
            addResponse(3, initializeActor());
        }

        @Actor
        public void actor4() {
            addResponse(4, initializeActor());
        }

        @Arbiter
        public void arbiter(final IIII_Result result) {
            updateResult(result);
        }
    }

    @JCStressTest
    @Outcome(id = "1, 1, 1, 1", expect = ACCEPTABLE)
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @State
    public static class OneAckFourRequests extends MostRelevantResponseHandlerStressTest {

        public OneAckFourRequests() {
            super(1, 4);
        }

        @Actor
        public void actor1() {
            addResponse(1, initializeActor());
        }

        @Actor
        public void actor2() {
            addResponse(2, initializeActor());
        }

        @Actor
        public void actor3() {
            addResponse(3, initializeActor());
        }

        @Actor
        public void actor4() {
            addResponse(4, initializeActor());
        }

        @Arbiter
        public void arbiter(final IIII_Result result) {
            updateResult(result);
        }
    }

    @JCStressTest
    @Outcome(id = "1, 1, 1, 1", expect = ACCEPTABLE)
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @State
    public static class OneAckThreeRequests extends MostRelevantResponseHandlerStressTest {

        public OneAckThreeRequests() {
            super(1, 3);
        }

        @Actor
        public void actor1() {
            addResponse(1, initializeActor());
        }

        @Actor
        public void actor2() {
            addResponse(2, initializeActor());
        }

        @Actor
        public void actor3() {
            addResponse(3, initializeActor());
        }

        @Arbiter
        public void arbiter(final IIII_Result result) {
            updateResult(result);
        }
    }
}
```
