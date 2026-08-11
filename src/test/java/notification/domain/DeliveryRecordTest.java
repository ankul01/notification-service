package notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRecordTest {

    private final ProviderContext providerContext =
            new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");

    @Test
    void pending_capturesAllFieldsAndStartsPending() {
        DeliveryRecord record = DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL, providerContext);

        assertThat(record.id()).isNotBlank();
        assertThat(record.tenantId()).isEqualTo("acme");
        assertThat(record.requestId()).isEqualTo("req-1");
        assertThat(record.recipientId()).isEqualTo("alice");
        assertThat(record.channel()).isEqualTo(ChannelType.EMAIL);
        assertThat(record.templateId()).isEqualTo("welcome-email");
        assertThat(record.params()).containsEntry("name", "Alice");
        assertThat(record.priority()).isEqualTo(Priority.TRANSACTIONAL);
        assertThat(record.providerContext()).isSameAs(providerContext);
        assertThat(record.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(record.deadReason()).isNull();
        assertThat(record.attempts()).isEmpty();
    }

    @Test
    void pending_assignsUniqueIdsAcrossCalls() {
        DeliveryRecord first = pendingRecord();
        DeliveryRecord second = pendingRecord();

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void markSent_transitionsToSentAndAdvancesUpdatedAt() {
        DeliveryRecord record = pendingRecord();
        Instant createdAt = record.updatedAt();

        record.markSent();

        assertThat(record.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(record.updatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void markDead_transitionsToDeadAndRecordsReason() {
        DeliveryRecord record = pendingRecord();

        record.markDead("simulated vendor outage");

        assertThat(record.status()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(record.deadReason()).isEqualTo("simulated vendor outage");
    }

    @Test
    void addAttempt_accumulatesInOrder() {
        DeliveryRecord record = pendingRecord();

        record.addAttempt(new DeliveryAttempt(1, Instant.now(), false, "timeout"));
        record.addAttempt(new DeliveryAttempt(2, Instant.now(), true, null));

        assertThat(record.attempts()).hasSize(2);
        assertThat(record.attempts().get(0).attemptNumber()).isEqualTo(1);
        assertThat(record.attempts().get(0).success()).isFalse();
        assertThat(record.attempts().get(1).attemptNumber()).isEqualTo(2);
        assertThat(record.attempts().get(1).success()).isTrue();
    }

    @Test
    void attempts_returnsASnapshotNotALiveView() {
        DeliveryRecord record = pendingRecord();
        record.addAttempt(new DeliveryAttempt(1, Instant.now(), true, null));

        var snapshot = record.attempts();
        record.addAttempt(new DeliveryAttempt(2, Instant.now(), true, null));

        assertThat(snapshot).hasSize(1); // the earlier snapshot must not see the later attempt
        assertThat(record.attempts()).hasSize(2);
    }

    private DeliveryRecord pendingRecord() {
        return DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL, providerContext);
    }
}
