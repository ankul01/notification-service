package notification.core;

import notification.domain.ChannelType;
import notification.domain.DeliveryRecord;
import notification.domain.DeliveryResult;
import notification.domain.DeliveryStatus;
import notification.domain.Priority;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.channel.Channel;
import notification.spi.channel.ChannelFactory;
import notification.spi.port.DeadLetterQueue;
import notification.spi.port.NotificationRepository;
import notification.spi.port.TemplateEngine;
import notification.spi.resilience.CircuitBreaker;
import notification.spi.resilience.CircuitBreakerRegistry;
import notification.spi.resilience.CircuitOpenException;
import notification.spi.resilience.RetriesExhaustedException;
import notification.spi.resilience.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock private NotificationRepository repo;
    @Mock private TemplateEngine templateEngine;
    @Mock private ChannelFactory channelFactory;
    @Mock private CircuitBreakerRegistry breakers;
    @Mock private RetryPolicy retryPolicy;
    @Mock private DeadLetterQueue dlq;
    @Mock private Channel channel;
    @Mock private CircuitBreaker breaker;

    private NotificationWorker worker;

    private final ProviderContext providerCtx =
            new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");
    private final RenderedMessage renderedMessage = new RenderedMessage(ChannelType.EMAIL, "alice", "subject", "body");

    @BeforeEach
    void setUp() {
        worker = new NotificationWorker(repo, templateEngine, channelFactory, breakers, retryPolicy, dlq);
    }

    @Test
    void handle_successfulDelivery_marksSentAndRecordsOneAttempt() throws Exception {
        DeliveryRecord record = pendingRecord();
        when(repo.load(record.id())).thenReturn(record);
        when(templateEngine.render("acme", "welcome-email", "alice", record.params())).thenReturn(renderedMessage);
        when(channelFactory.forType(ChannelType.EMAIL)).thenReturn(channel);
        when(breakers.forProvider(ChannelType.EMAIL)).thenReturn(breaker);
        when(channel.deliver(renderedMessage, providerCtx)).thenReturn(DeliveryResult.success("provider-msg-id"));
        delegateBreakerAndRetryToTheAction();

        worker.handle(record.id());

        assertThat(record.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(record.attempts()).hasSize(1);
        assertThat(record.attempts().get(0).success()).isTrue();
        verify(repo).update(record);
        verifyNoInteractions(dlq);
    }

    @Test
    void handle_retriesExhausted_marksDeadAndPushesToDlq() throws Exception {
        DeliveryRecord record = pendingRecord();
        when(repo.load(record.id())).thenReturn(record);
        when(templateEngine.render("acme", "welcome-email", "alice", record.params())).thenReturn(renderedMessage);
        when(channelFactory.forType(ChannelType.EMAIL)).thenReturn(channel);
        when(breakers.forProvider(ChannelType.EMAIL)).thenReturn(breaker);
        delegateBreakerToTheAction();
        RetriesExhaustedException exhausted = new RetriesExhaustedException(
                "Exhausted 3 attempts: simulated vendor outage", new RuntimeException("simulated vendor outage"));
        when(retryPolicy.execute(any())).thenThrow(exhausted);

        worker.handle(record.id());

        assertThat(record.status()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(record.deadReason()).isEqualTo(exhausted.getMessage());
        verify(repo).update(record);
        verify(dlq).push(record);
    }

    @Test
    void handle_circuitOpen_marksDeadAndPushesToDlqWithoutInvokingRetryPolicy() throws Exception {
        DeliveryRecord record = pendingRecord();
        when(repo.load(record.id())).thenReturn(record);
        when(templateEngine.render("acme", "welcome-email", "alice", record.params())).thenReturn(renderedMessage);
        when(channelFactory.forType(ChannelType.EMAIL)).thenReturn(channel);
        when(breakers.forProvider(ChannelType.EMAIL)).thenReturn(breaker);
        CircuitOpenException circuitOpen = new CircuitOpenException("Circuit open; provider considered unhealthy");
        when(breaker.run(any())).thenThrow(circuitOpen);

        worker.handle(record.id());

        assertThat(record.status()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(record.deadReason()).isEqualTo(circuitOpen.getMessage());
        verify(repo).update(record);
        verify(dlq).push(record);
        verify(retryPolicy, never()).execute(any());
    }

    private void delegateBreakerAndRetryToTheAction() throws Exception {
        delegateBreakerToTheAction();
        when(retryPolicy.execute(any())).thenAnswer(invocation -> {
            Callable<?> action = invocation.getArgument(0);
            return action.call();
        });
    }

    private void delegateBreakerToTheAction() throws Exception {
        when(breaker.run(any())).thenAnswer(invocation -> {
            Callable<?> action = invocation.getArgument(0);
            return action.call();
        });
    }

    private DeliveryRecord pendingRecord() {
        return DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL, providerCtx);
    }
}
