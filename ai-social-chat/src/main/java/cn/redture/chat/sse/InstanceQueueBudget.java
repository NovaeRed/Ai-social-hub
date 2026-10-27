package cn.redture.chat.sse;

import java.util.concurrent.atomic.AtomicLong;

/** 管理实例共享的排队字节额度。 */
final class InstanceQueueBudget {
    private final long limit;
    private final AtomicLong used = new AtomicLong();

    InstanceQueueBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("queue byte budget must be positive");
        this.limit = limit;
    }

    /**
     * 原子预留实例排队字节额度，不等待空闲额度。
     *
     * @param bytes 待预留字节数
     * @return 是否预留成功；参数无效或额度不足时为 false
     */
    boolean tryReserve(long bytes) {
        if (bytes < 0 || bytes > limit) return false;
        long current;
        do {
            current = used.get();
            if (bytes > limit - current) return false;
        } while (!used.compareAndSet(current, current + bytes));
        return true;
    }

    /**
     * 归还事件出队或关闭时释放的实例字节额度。
     *
     * @param bytes 待归还字节数
     */
    void release(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative byte release");
        used.addAndGet(-bytes);
    }

    long usedBytes() {
        return used.get();
    }
}
