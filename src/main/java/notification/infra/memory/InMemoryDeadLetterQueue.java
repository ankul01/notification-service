package notification.infra.memory;

import notification.domain.DeliveryRecord;
import notification.spi.port.DeadLetterQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryDeadLetterQueue implements DeadLetterQueue {
    private final List<DeliveryRecord> dead = new CopyOnWriteArrayList<>();

    @Override
    public void push(DeliveryRecord record) {
        dead.add(record);
    }

    public List<DeliveryRecord> snapshot() {
        return new ArrayList<>(dead);
    }
}
