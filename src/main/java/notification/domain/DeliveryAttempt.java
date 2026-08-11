package notification.domain;

import java.time.Instant;

/** One attempt to hand a message to a provider; a DeliveryRecord accumulates these across retries. */
public final class DeliveryAttempt {
    private final int attemptNumber;
    private final Instant attemptedAt;
    private final boolean success;
    private final String errorMessage;

    public DeliveryAttempt(int attemptNumber, Instant attemptedAt, boolean success, String errorMessage) {
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Instant attemptedAt() {
        return attemptedAt;
    }

    public boolean success() {
        return success;
    }

    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "Attempt#" + attemptNumber + "[" + (success ? "OK" : "FAIL: " + errorMessage) + "]";
    }
}
