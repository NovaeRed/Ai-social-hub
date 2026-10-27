package cn.redture.chat.sse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** 汇总固定指标码的计数、耗时和滚动窗口数据。 */
public final class SseMetrics {
    private final boolean enabled;
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Latency dispatch = new Latency();
    private final Latency queueWait = new Latency();
    private final Latency enqueueWait = new Latency();
    private final Latency write = new Latency();
    private final Window window;

    SseMetrics(boolean enabled) {
        this(enabled, 60_000);
    }

    SseMetrics(boolean enabled, long windowMs) {
        this.enabled = enabled;
        this.window = new Window(windowMs);
    }

    void criticalAttempt(boolean rejected) {
        if (enabled) window.criticalAttempt(rejected, System.nanoTime());
    }

    Map<String, Long> recent() {
        return window.snapshot(System.nanoTime());
    }

    void increment(String code) {
        add(code, 1);
    }

    /**
     * 启用采集时，按固定指标码累加计数。
     *
     * @param code 固定指标码，不含用户或连接标识
     * @param amount 本次增加的数量
     */
    void add(String code, long amount) {
        if (enabled) counters.computeIfAbsent(code, ignored -> new LongAdder()).add(amount);
    }

    void dispatchDuration(long nanos) {
        if (enabled) dispatch.record(nanos);
    }

    /**
     * 同时记录累计及滚动窗口内的排队耗时。
     *
     * @param nanos 事件排队时长，单位为纳秒
     */
    void queueWait(long nanos) {
        if (enabled) {
            queueWait.record(nanos);
            window.queueWait(nanos, System.nanoTime());
        }
    }

    void enqueueWait(long nanos) {
        if (enabled) enqueueWait.record(nanos);
    }

    void writeDuration(long nanos) {
        if (enabled) write.record(nanos);
    }

    /**
     * 汇总当前计数，隔离外部对内部统计的修改。
     *
     * @return 按指标码分组的只读计数快照
     */
    public Map<String, Long> counters() {
        var result = new TreeMap<String, Long>();
        counters.forEach((key, value) -> result.put(key, value.sum()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 汇总分发、排队、入队等待和写入的累计耗时指标。
     *
     * @return 各阶段的样本数、纳秒总耗时及 P99 毫秒上界
     */
    public Map<String, Map<String, Long>> latency() {
        return Map.of(
                "dispatch",
                dispatch.snapshot(),
                "queueWait",
                queueWait.snapshot(),
                "enqueueWait",
                enqueueWait.snapshot(),
                "write",
                write.snapshot());
    }

    /** 使用十二个固定时间桶统计近期样本。 */
    static final class Window {
        private final long bucketNanos;
        private final long configuredWindowMs;
        private final Bucket[] buckets =
                java.util.stream.IntStream.range(0, 12)
                        .mapToObj(i -> new Bucket())
                        .toArray(Bucket[]::new);

        Window(long windowMs) {
            configuredWindowMs = windowMs;
            bucketNanos =
                    Math.max(
                            1,
                            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(windowMs)
                                    / buckets.length);
        }

        /**
         * 定位当前时间桶，复用旧桶时清空过期样本。
         *
         * @param now 当前单调时间，单位为纳秒
         * @return 当前可记录样本的时间桶
         */
        private Bucket current(long now) {
            long epoch = Math.floorDiv(now, bucketNanos);
            var bucket = buckets[Math.floorMod(epoch, buckets.length)];
            if (bucket.epoch != epoch) {
                bucket.epoch = epoch;
                bucket.attempted = bucket.rejected = 0;
                Arrays.fill(bucket.queue, 0);
            }
            return bucket;
        }

        /**
         * 记录滚动窗口内的一次关键事件入队尝试。
         *
         * @param rejected 本次尝试是否被拒绝
         * @param now 当前单调时间，单位为纳秒
         */
        synchronized void criticalAttempt(boolean rejected, long now) {
            var bucket = current(now);
            bucket.attempted++;
            if (rejected) bucket.rejected++;
        }

        /**
         * 按耗时上界将排队样本计入当前时间桶。
         *
         * @param nanos 排队时长，单位为纳秒
         * @param now 当前单调时间，单位为纳秒
         */
        synchronized void queueWait(long nanos, long now) {
            var bucket = current(now);
            for (int i = 0; i < Latency.BOUNDS.length; i++) {
                long bound = Latency.BOUNDS[i];
                if (bound == Long.MAX_VALUE
                        || nanos <= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(bound)) {
                    bucket.queue[i]++;
                    break;
                }
            }
        }

        /**
         * 汇总有效时间桶，计算关键事件拒绝数和排队 P99 上界。
         *
         * @param now 当前单调时间，单位为纳秒
         * @return 窗口大小、尝试和拒绝数、排队样本数及 P99 毫秒上界
         */
        synchronized Map<String, Long> snapshot(long now) {
            long epoch = Math.floorDiv(now, bucketNanos), attempted = 0, rejected = 0;
            long[] queue = new long[Latency.BOUNDS.length];
            for (var bucket : buckets)
                if (epoch - bucket.epoch >= 0 && epoch - bucket.epoch < buckets.length) {
                    attempted += bucket.attempted;
                    rejected += bucket.rejected;
                    for (int i = 0; i < queue.length; i++) queue[i] += bucket.queue[i];
                }
            long samples = Arrays.stream(queue).sum(), accumulated = 0, p99 = 0;
            for (int i = 0; i < queue.length && samples > 0; i++) {
                accumulated += queue[i];
                if (accumulated >= Math.ceil(samples * .99)) {
                    p99 = Latency.BOUNDS[i];
                    break;
                }
            }
            return Map.of(
                    "windowMs",
                    configuredWindowMs,
                    "criticalAttempts",
                    attempted,
                    "criticalRejected",
                    rejected,
                    "queueSamples",
                    samples,
                    "queueP99UpperBoundMs",
                    p99);
        }

        /** 保存单个时间桶的计数和耗时分布。 */
        private static final class Bucket {
            long epoch = Long.MIN_VALUE / 2;
            long attempted, rejected;
            final long[] queue = new long[Latency.BOUNDS.length];
        }
    }

    /** 按固定耗时上界汇总累计样本。 */
    private static final class Latency {
        // 上界单位为毫秒，末桶接纳溢出样本；P99 使用桶上界。
        private static final long[] BOUNDS = {
            1, 5, 10, 25, 50, 100, 250, 500, 1000, 5000, 30_000, Long.MAX_VALUE
        };
        private final LongAdder[] buckets =
                Arrays.stream(BOUNDS)
                        .mapToObj(ignored -> new LongAdder())
                        .toArray(LongAdder[]::new);
        private final LongAdder totalNanos = new LongAdder();

        /**
         * 将非负耗时计入累计总量及对应分桶。
         *
         * @param nanos 本次耗时，单位为纳秒；负数按 0 处理
         */
        void record(long nanos) {
            totalNanos.add(Math.max(0, nanos));
            long safeNanos = Math.max(0, nanos);
            long millis = safeNanos / 1_000_000 + (safeNanos % 1_000_000 == 0 ? 0 : 1);
            for (int i = 0; i < BOUNDS.length; i++)
                if (millis <= BOUNDS[i]) {
                    buckets[i].increment();
                    break;
                }
        }

        /**
         * 汇总累计耗时，并以分桶上界估计 P99。
         *
         * @return 样本数、纳秒总耗时及 P99 毫秒上界
         */
        Map<String, Long> snapshot() {
            long count = Arrays.stream(buckets).mapToLong(LongAdder::sum).sum();
            long accumulated = 0, p99 = 0;
            for (int i = 0; i < buckets.length && count > 0; i++) {
                accumulated += buckets[i].sum();
                if (accumulated >= Math.ceil(count * .99)) {
                    p99 = BOUNDS[i];
                    break;
                }
            }
            return Map.of("count", count, "totalNanos", totalNanos.sum(), "p99UpperBoundMs", p99);
        }
    }
}
