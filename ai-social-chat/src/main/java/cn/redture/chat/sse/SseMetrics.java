package cn.redture.chat.sse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded-cardinality counters; no user/client/event identifiers become labels. */
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

    void add(String code, long amount) {
        if (enabled) counters.computeIfAbsent(code, ignored -> new LongAdder()).add(amount);
    }

    void dispatchDuration(long nanos) {
        if (enabled) dispatch.record(nanos);
    }

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

    public Map<String, Long> counters() {
        var result = new TreeMap<String, Long>();
        counters.forEach((key, value) -> result.put(key, value.sum()));
        return Collections.unmodifiableMap(result);
    }

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

    /** Twelve fixed buckets, bounded memory. Oldest partial bucket may be excluded. */
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

        synchronized void criticalAttempt(boolean rejected, long now) {
            var bucket = current(now);
            bucket.attempted++;
            if (rejected) bucket.rejected++;
        }

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

        private static final class Bucket {
            long epoch = Long.MIN_VALUE / 2;
            long attempted, rejected;
            final long[] queue = new long[Latency.BOUNDS.length];
        }
    }

    private static final class Latency {
        // Upper bounds in milliseconds. Last bucket is overflow; p99 is a bucket upper bound.
        private static final long[] BOUNDS = {
            1, 5, 10, 25, 50, 100, 250, 500, 1000, 5000, 30_000, Long.MAX_VALUE
        };
        private final LongAdder[] buckets =
                Arrays.stream(BOUNDS)
                        .mapToObj(ignored -> new LongAdder())
                        .toArray(LongAdder[]::new);
        private final LongAdder totalNanos = new LongAdder();

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
