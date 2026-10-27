package cn.redture.chat.sse;

import java.util.concurrent.atomic.AtomicLong;

/** Shared queued-byte budget. Reservations never block the registry or queue owner. */
final class InstanceQueueBudget {
    private final long limit;
    private final AtomicLong used = new AtomicLong();

    InstanceQueueBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("queue byte budget must be positive");
        this.limit = limit;
    }

    boolean tryReserve(long bytes) {
        if (bytes < 0 || bytes > limit) return false;
        long current;
        do {
            current = used.get();
            if (bytes > limit - current) return false;
        } while (!used.compareAndSet(current, current + bytes));
        return true;
    }

    void release(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative byte release");
        used.addAndGet(-bytes);
    }

    long usedBytes() {
        return used.get();
    }
}
