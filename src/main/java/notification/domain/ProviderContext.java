package notification.domain;

/** Per-tenant provider configuration (API keys, sender id, endpoint, etc.) for a channel. */
public final class ProviderContext {
    private final String tenantId;
    private final ChannelType channel;
    private final String providerName;
    private final String senderId;

    public ProviderContext(String tenantId, ChannelType channel, String providerName, String senderId) {
        this.tenantId = tenantId;
        this.channel = channel;
        this.providerName = providerName;
        this.senderId = senderId;
    }

    public String tenantId() {
        return tenantId;
    }

    public ChannelType channel() {
        return channel;
    }

    public String providerName() {
        return providerName;
    }

    public String senderId() {
        return senderId;
    }
}
