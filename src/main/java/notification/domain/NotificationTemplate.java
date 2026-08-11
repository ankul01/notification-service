package notification.domain;

/** Per-tenant template. body uses {{paramName}} placeholders, resolved by TemplateEngine. */
public final class NotificationTemplate {
    private final String templateId;
    private final String tenantId;
    private final ChannelType channel;
    private final String subject;
    private final String body;

    public NotificationTemplate(String templateId, String tenantId, ChannelType channel,
                                 String subject, String body) {
        this.templateId = templateId;
        this.tenantId = tenantId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
    }

    public String templateId() {
        return templateId;
    }

    public String tenantId() {
        return tenantId;
    }

    public ChannelType channel() {
        return channel;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }
}
