package notification.infra.resilience;

import notification.spi.resilience.CircuitBreaker;
import notification.spi.resilience.CircuitOpenException;

import java.util.concurrent.Callable;

/**
 * Classic 3-state breaker: CLOSED (normal) -> OPEN (trip after N consecutive failures, reject
 * fast) -> HALF_OPEN (after a cooldown, let one probe through) -> CLOSED on success or back to
 * OPEN on failure. Scoped per-provider so a dead SMS vendor can't take down email delivery.
 */
public final class SimpleCircuitBreaker implements CircuitBreaker {
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long cooldownMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0;

    public SimpleCircuitBreaker(int failureThreshold, long cooldownMillis) {
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownMillis;
    }

    @Override
    public <T> T run(Callable<T> action) throws CircuitOpenException {
        // Lock only guards the state machine; the call itself (including retry backoff sleeps)
        // runs outside the lock so concurrent callers aren't serialized behind one slow attempt.
        checkStateBeforeCall();
        try {
            T result = action.call();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e; // preserve RetriesExhaustedException / CircuitOpenException as thrown
        } catch (Exception e) {
            onFailure();
            throw new RuntimeException(e);
        }
    }

    private synchronized void checkStateBeforeCall() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= cooldownMillis) {
                state = State.HALF_OPEN;
            } else {
                throw new CircuitOpenException("Circuit open; provider considered unhealthy");
            }
        }
    }

    private synchronized void onSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
        }
    }
}
