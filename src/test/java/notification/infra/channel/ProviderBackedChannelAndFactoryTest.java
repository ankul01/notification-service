package notification.infra.channel;

import notification.domain.ChannelType;
import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.channel.Channel;
import notification.spi.port.ProviderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderBackedChannelAndFactoryTest {

    @Mock
    private ProviderClient providerClient;

    private final RenderedMessage message = new RenderedMessage(ChannelType.EMAIL, "alice", "subject", "body");
    private final ProviderContext ctx =
            new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");

    @Test
    void deliver_delegatesDirectlyToTheProviderClientResult() {
        DeliveryResult expected = DeliveryResult.success("provider-msg-id");
        when(providerClient.send(message, ctx)).thenReturn(expected);

        ProviderBackedChannel channel = new ProviderBackedChannel(ChannelType.EMAIL, providerClient);
        DeliveryResult actual = channel.deliver(message, ctx);

        assertThat(actual).isSameAs(expected);
        verify(providerClient).send(message, ctx);
    }

    @Test
    void deliver_propagatesAFailureResultUnchanged() {
        DeliveryResult failure = DeliveryResult.failure("simulated vendor outage");
        when(providerClient.send(message, ctx)).thenReturn(failure);

        ProviderBackedChannel channel = new ProviderBackedChannel(ChannelType.EMAIL, providerClient);

        assertThat(channel.deliver(message, ctx)).isSameAs(failure);
    }

    @Test
    void type_returnsTheConfiguredChannelType() {
        ProviderBackedChannel smsChannel = new ProviderBackedChannel(ChannelType.SMS, providerClient);

        assertThat(smsChannel.type()).isEqualTo(ChannelType.SMS);
    }

    @Test
    void factory_forType_returnsTheRegisteredChannel() {
        Channel emailChannel = new ProviderBackedChannel(ChannelType.EMAIL, providerClient);
        DefaultChannelFactory factory = new DefaultChannelFactory().register(emailChannel);

        assertThat(factory.forType(ChannelType.EMAIL)).isSameAs(emailChannel);
    }

    @Test
    void factory_forType_throwsForAnUnregisteredChannel() {
        DefaultChannelFactory factory = new DefaultChannelFactory();

        assertThatThrownBy(() -> factory.forType(ChannelType.PUSH))
                .isInstanceOf(IllegalStateException.class);
    }
}
