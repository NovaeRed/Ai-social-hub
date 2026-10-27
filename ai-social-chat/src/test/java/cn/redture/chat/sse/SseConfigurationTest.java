package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SseConfigurationTest {
    @Test
    void defaultsObserveWithoutImposingUncalibratedWriteBudget() {
        var config = new PushConfig();
        assertDoesNotThrow(config::validate);
        assertEquals(0, config.getWriteStallTimeoutMs());
    }

    @Test
    void rejectsInvalidLimitsAndUnboundedOrUnsupportedTimeouts() {
        var config = new PushConfig();
        config.setMaxConnectionsPerUser(0);
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setMaxConnectionsPerUser(1);
        config.setWriteStallTimeoutMs(1);
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setTransportWriteTimeoutMs(100);
        assertDoesNotThrow(config::validate);
        config.getPriorityStrategy().setLowDiscardThreshold(Double.NaN);
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
