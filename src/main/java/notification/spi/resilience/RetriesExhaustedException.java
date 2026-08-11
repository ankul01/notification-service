package notification.spi.resilience;

public class RetriesExhaustedException extends RuntimeException {
    public RetriesExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
