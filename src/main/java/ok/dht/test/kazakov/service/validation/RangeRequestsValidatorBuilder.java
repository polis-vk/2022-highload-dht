package ok.dht.test.kazakov.service.validation;

import javax.annotation.Nullable;

public final class RangeRequestsValidatorBuilder {

    private final ThreadLocal<Validator> validator = ThreadLocal.withInitial(Validator::new);

    public Validator validate() {
        return validator.get().setValid();
    }

    public static final class Validator extends AbstractValidator<Validator> {

        @Override
        protected Validator getSelf() {
            return this;
        }

        public Validator validateStart(@Nullable final String start) {
            if (isInvalid()) {
                return this;
            }

            if (start == null) {
                return setInvalid("Start should not be absent");
            }

            if (start.isEmpty()) {
                return setInvalid("Start should not be empty");
            }

            return this;
        }

        public Validator validateEnd(@Nullable final String end) {
            if (isInvalid() || end == null) {
                return this;
            }

            if (end.isEmpty()) {
                return setInvalid("End should not be empty");
            }

            return this;
        }

        public Validator validateRange(@Nullable final String start,
                                       @Nullable final String end) {
            validateStart(start);
            validateEnd(end);

            if (isInvalid()) {
                return this;
            }

            if (end != null && start != null && start.compareTo(end) > 0) {
                return setInvalid("Start should be greater or equal to end");
            }

            return this;
        }
    }
}
