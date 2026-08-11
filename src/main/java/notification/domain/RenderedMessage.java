package notification.domain;

/** Template + params resolved into a channel-ready message body. */
public final class RenderedMessage {
    private final ChannelType channel;
    private final String recipientId;
    private final String subject;
    private final String body;

    public RenderedMessage(ChannelType channel, String recipientId, String subject, String body) {
        this.channel = channel;
        this.recipientId = recipientId;
        this.subject = subject;
        this.body = body;
    }

    public ChannelType channel() {
        return channel;
    }

    public String recipientId() {
        return recipientId;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }
}
