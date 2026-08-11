package notification.boot;

import notification.api.DeliveryHandle;
import notification.api.NotificationRequest;
import notification.api.NotificationService;
import notification.domain.ChannelType;
import notification.domain.DeliveryStatus;
import notification.domain.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real wiring end to end: send() -> outbox -> OutboxDispatcher (background thread)
 * -> NotificationWorker -> the seeded "acme" tenant's ReliableProviderClient. Uses the demo data
 * BeanConfiguration seeds (tenant "acme", recipient "alice", template "welcome-email").
 */
@SpringBootTest
class NotificationServiceEndToEndTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    void send_forTheSeededDemoTenant_eventuallyReachesSent() {
        String requestId = "e2e-" + UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(requestId, "acme", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice", "tenant", "Acme"), Priority.TRANSACTIONAL);

        DeliveryHandle handle = notificationService.send(request);
        assertThat(handle.status()).isEqualTo(DeliveryStatus.PENDING);

        assertThat(pollUntilSent(requestId)).isEqualTo(DeliveryStatus.SENT);
    }

    /** Delivery completes on a background thread (OutboxDispatcher -> NotificationWorker); poll for it. */
    private DeliveryStatus pollUntilSent(String requestId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        DeliveryStatus status;
        do {
            status = notificationService.status("acme", requestId);
            if (status == DeliveryStatus.SENT) {
                return status;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return status;
            }
        } while (Instant.now().isBefore(deadline));
        return status;
    }

    @Test
    void send_isIdempotent_duplicateAfterCompletionReturnsTheSameTerminalOutcome() {
        // Wait for the first send to finish before firing the duplicate — asserting status
        // equality on two sends fired back-to-back would race the background dispatcher thread
        // (the second call could observe SENT while `first` was captured as PENDING).
        String requestId = "e2e-dup-" + UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(requestId, "acme", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice", "tenant", "Acme"), Priority.TRANSACTIONAL);

        notificationService.send(request);
        assertThat(pollUntilSent(requestId)).isEqualTo(DeliveryStatus.SENT);

        DeliveryHandle duplicate = notificationService.send(request);

        assertThat(duplicate.requestId()).isEqualTo(requestId);
        assertThat(duplicate.status()).isEqualTo(DeliveryStatus.SENT);
    }
}
