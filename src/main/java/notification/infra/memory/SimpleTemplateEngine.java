package notification.infra.memory;

import notification.domain.NotificationTemplate;
import notification.domain.RenderedMessage;
import notification.spi.port.TemplateEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders {{paramName}} placeholders. A real implementation would use a sandboxed template lib. */
public final class SimpleTemplateEngine implements TemplateEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, NotificationTemplate> templates = new ConcurrentHashMap<>();

    public SimpleTemplateEngine register(NotificationTemplate template) {
        templates.put(key(template.tenantId(), template.templateId()), template);
        return this;
    }

    @Override
    public RenderedMessage render(String tenantId, String templateId, String recipientId, Map<String, Object> params) {
        NotificationTemplate template = templates.get(key(tenantId, templateId));
        if (template == null) {
            throw new IllegalArgumentException("No template " + templateId + " for tenant " + tenantId);
        }
        return new RenderedMessage(
                template.channel(),
                recipientId,
                interpolate(template.subject(), params),
                interpolate(template.body(), params));
    }

    private static String interpolate(String text, Map<String, Object> params) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = params.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String key(String tenantId, String templateId) {
        return tenantId + ":" + templateId;
    }
}
