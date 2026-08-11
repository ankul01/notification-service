package notification.spi.port;

import notification.domain.RenderedMessage;

import java.util.Map;

public interface TemplateEngine {
    RenderedMessage render(String tenantId, String templateId, String recipientId, Map<String, Object> params);
}
