package notification.spi;

/** Thrown by the worker when Channel.deliver() reports failure, so RetryPolicy/CircuitBreaker can react to it. */
public class DeliveryFailedException extends RuntimeException {
    public DeliveryFailedException(String message) {
        super(message);
    }
}
