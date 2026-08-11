package notification.infra.memory;

import notification.domain.ChannelType;
import notification.domain.NotificationTemplate;
import notification.domain.RenderedMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleTemplateEngineTest {

    @Test
    void render_substitutesAllPlaceholders() {
        SimpleTemplateEngine engine = new SimpleTemplateEngine().register(
                new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL,
                        "Welcome, {{name}}!", "Hi {{name}}, thanks for joining {{tenant}}."));

        RenderedMessage msg = engine.render("acme", "welcome-email", "alice",
                Map.of("name", "Alice", "tenant", "Acme"));

        assertThat(msg.channel()).isEqualTo(ChannelType.EMAIL);
        assertThat(msg.recipientId()).isEqualTo("alice");
        assertThat(msg.subject()).isEqualTo("Welcome, Alice!");
        assertThat(msg.body()).isEqualTo("Hi Alice, thanks for joining Acme.");
    }

    @Test
    void render_throwsForUnknownTemplate() {
        SimpleTemplateEngine engine = new SimpleTemplateEngine();

        assertThatThrownBy(() -> engine.render("acme", "no-such-template", "alice", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void render_isScopedPerTenant() {
        SimpleTemplateEngine engine = new SimpleTemplateEngine().register(
                new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL, "Hi", "Hi"));

        assertThatThrownBy(() -> engine.render("other-tenant", "welcome-email", "alice", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void render_missingParamSubstitutesEmptyString() {
        // Documents current behavior (REVIEW.md H-05, open/deferred): a missing param silently
        // renders as "", it does not fail. Not asserting this is desirable — just that it's what
        // happens today, so a future fix has a test to update rather than discover this by hand.
        SimpleTemplateEngine engine = new SimpleTemplateEngine().register(
                new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL,
                        "Welcome, {{name}}!", "no body params here"));

        RenderedMessage msg = engine.render("acme", "welcome-email", "alice", Map.of());

        assertThat(msg.subject()).isEqualTo("Welcome, !");
    }
}
