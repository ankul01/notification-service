package notification.domain;

public final class DeliveryResult {
    private final boolean ok;
    private final String providerMessageId;
    private final String errorMessage;

    private DeliveryResult(boolean ok, String providerMessageId, String errorMessage) {
        this.ok = ok;
        this.providerMessageId = providerMessageId;
        this.errorMessage = errorMessage;
    }

    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(true, providerMessageId, null);
    }

    public static DeliveryResult failure(String errorMessage) {
        return new DeliveryResult(false, null, errorMessage);
    }

    public boolean ok() {
        return ok;
    }

    public String providerMessageId() {
        return providerMessageId;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
