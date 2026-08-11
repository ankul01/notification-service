package notification.spi.port;

import notification.domain.DeliveryRecord;

/** Holds records that exhausted retries or hit an open circuit, for later inspection/replay. */
public interface DeadLetterQueue {
    void push(DeliveryRecord record);
}
