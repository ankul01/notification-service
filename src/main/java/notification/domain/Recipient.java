package notification.domain;

public final class Recipient {
    private final String recipientId;
    private final String tenantId;
    private final String email;
    private final String phone;
    private final String deviceToken;
    private final RecipientPreferences preferences;

    public Recipient(String recipientId, String tenantId, String email, String phone,
                      String deviceToken, RecipientPreferences preferences) {
        this.recipientId = recipientId;
        this.tenantId = tenantId;
        this.email = email;
        this.phone = phone;
        this.deviceToken = deviceToken;
        this.preferences = preferences;
    }

    public String recipientId() {
        return recipientId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String deviceToken() {
        return deviceToken;
    }

    public RecipientPreferences preferences() {
        return preferences;
    }
}
