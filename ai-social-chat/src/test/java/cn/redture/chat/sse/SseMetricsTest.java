package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SseMetricsTest {
    @Test
    void latencyBucketUpperBoundNeverRoundsSampleDown() {
        var metrics = new SseMetrics(true);
        metrics.writeDuration(1_900_000);
        assertEquals(5, metrics.latency().get("write").get("p99UpperBoundMs"));
        assertEquals(1, metrics.latency().get("write").get("count"));
    }

    @Test
    void rollingWindowRetainsNumeratorDenominatorAndExpiresOldSamples() {
        var window = new SseMetrics.Window(1200);
        window.criticalAttempt(true, 0);
        window.criticalAttempt(false, 50_000_000);
        window.queueWait(1_900_000, 50_000_000);
        var sample = window.snapshot(100_000_000);
        assertEquals(2, sample.get("criticalAttempts"));
        assertEquals(1, sample.get("criticalRejected"));
        assertEquals(5, sample.get("queueP99UpperBoundMs"));
        sample = window.snapshot(1_300_000_000);
        assertEquals(0, sample.get("criticalAttempts"));
        assertEquals(0, sample.get("queueSamples"));
    }
}
