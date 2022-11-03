package ok.dht.test.kazakov.service.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public final class DaoRequestsValidatorBuilder {

    private final ThreadLocal<Validator> validator = ThreadLocal.withInitial(Validator::new);

    public Validator validate() {
        return validator.get().setValid();
    }

    public static final class Validator extends AbstractValidator<Validator> {

        private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

        private int parsedInt1;
        private int parsedInt2;

        public int getParsedInt1() {
            return parsedInt1;
        }

        public int getParsedInt2() {
            return parsedInt2;
        }

        @Override
        protected Validator getSelf() {
            return this;
        }

        public Validator validateId(@Nullable final String id) {
            if (isInvalid()) {
                return this;
            }

            if (id == null) {
                return setInvalid("Id should not be absent");
            }

            if (id.isEmpty()) {
                return setInvalid("Id should not be empty");
            }

            return this;
        }

        public Validator validateValue(@Nullable final byte[] value) {
            if (isInvalid()) {
                return this;
            }

            if (value == null) {
                return setInvalid("Value should not be absent");
            }

            return this;
        }

        public Validator validateReplicas(@Nullable final String ack,
                                          @Nullable final String from,
                                          final int totalNodes) {
            if (isInvalid() || (ack == null && from == null)) {
                return this;
            }

            if (ack == null) {
                return setInvalid("Ack should be not null if from is not null");
            }

            if (from == null) {
                return setInvalid("From should be not null if ack is not null");
            }

            try {
                parsedInt1 = Integer.parseInt(ack);
            } catch (final NumberFormatException e) {
                LOG.debug("Could not parse ack={}", ack, e);
                return setInvalid("Ack should be a 32-bit integer");
            }
            final int parsedAck = parsedInt1;

            try {
                parsedInt2 = Integer.parseInt(from);
            } catch (final NumberFormatException e) {
                LOG.debug("Could not parse from={}", from, e);
                return setInvalid("From should be a 32-bit integer");
            }
            final int parsedFrom = parsedInt2;

            if (parsedAck <= 0) {
                return setInvalid("Ack should be positive");
            }

            if (parsedFrom <= 0) {
                return setInvalid("From should be positive");
            }

            if (parsedAck > parsedFrom) {
                return setInvalid("Ack should be not greater than from");
            }

            if (parsedFrom > totalNodes) {
                return setInvalid("From should be not greater than total nodes (" + totalNodes + ")");
            }

            return this;
        }
    }
}
