package notification.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One dedicated thread pool per tenant. A tenant flooding the queue only exhausts its own pool,
 * so a noisy neighbour can't starve other tenants' workers of threads.
 */
public final class TenantBulkheadPool {
    private final Map<String, ExecutorService> pools = new ConcurrentHashMap<>();
    private final int threadsPerTenant;

    public TenantBulkheadPool(int threadsPerTenant) {
        this.threadsPerTenant = threadsPerTenant;
    }

    public ExecutorService forTenant(String tenantId) {
        return pools.computeIfAbsent(tenantId, this::newPool);
    }

    private ExecutorService newPool(String tenantId) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "worker-" + tenantId + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(threadsPerTenant, factory);
    }

    public void shutdown() {
        pools.values().forEach(ExecutorService::shutdownNow);
    }
}
