package notification.api;

import notification.domain.DeliveryStatus;

/** Returned synchronously from send(); the real outcome is fetched later via status(). */
public final class DeliveryHandle {
    private final String requestId;
    private final DeliveryStatus status;

    private DeliveryHandle(String requestId, DeliveryStatus status) {
        this.requestId = requestId;
        this.status = status;
    }

    public static DeliveryHandle of(String requestId, DeliveryStatus status) {
        return new DeliveryHandle(requestId, status);
    }

    public static DeliveryHandle throttled(String requestId) {
        return new DeliveryHandle(requestId, DeliveryStatus.THROTTLED);
    }

    public String requestId() {
        return requestId;
    }

    public DeliveryStatus status() {
        return status;
    }

    @Override
    public String toString() {
        return "DeliveryHandle{requestId=" + requestId + ", status=" + status + "}";
    }
}
