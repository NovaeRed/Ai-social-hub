package cn.redture.aiEngine.handler;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;

import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.notification.DispatchResult;
import cn.redture.common.integration.notification.NotificationExternalService;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

class AiNotificationTaskHandlerTest {
    private static final String TASK =
            "{\"aiTaskId\":1,\"taskPublicId\":\"job-1\",\"status\":\"COMPLETED\"}";

    @Test
    void everyAdmissionOutcomeCompletesHintDispositionAndLogsItsActualResult() throws Exception {
        DispatchResult[] outcomes = {
            DispatchResult.noActiveConnection(),
            new DispatchResult(2, 2, 0, 0),
            new DispatchResult(2, 1, 1, 0),
            new DispatchResult(2, 0, 2, 0),
            new DispatchResult(2, 0, 0, 2)
        };
        Logger logger = (Logger) LoggerFactory.getLogger(AiNotificationTaskHandler.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            for (DispatchResult outcome : outcomes) {
                AtomicInteger attempts = new AtomicInteger();
                NotificationExternalService bridge =
                        (user, type, payload) -> {
                            assertEquals(1L, user);
                            assertEquals("AI_TASK_COMPLETED", type);
                            assertEquals("job-1", payload.get("taskPublicId"));
                            attempts.incrementAndGet();
                            return outcome;
                        };
                new AiNotificationTaskHandler(bridge)
                        .executeTask(TASK, 1L, "AI_TASK_COMPLETED", "1-0");
                assertEquals(1, attempts.get());
                String message = logs.list.getLast().getFormattedMessage();
                assertTrue(message.contains(outcome.status().name()), message);
                assertFalse(message.contains("通知已推送"), message);
            }
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void bridgeFailurePropagatesSoConsumerCanRetry() {
        RuntimeException failure = new RuntimeException("unavailable");
        AiNotificationTaskHandler handler =
                new AiNotificationTaskHandler(
                        (u, t, p) -> {
                            throw failure;
                        });
        assertSame(
                failure,
                assertThrows(
                        RuntimeException.class,
                        () -> handler.executeTask(TASK, 1L, "AI_TASK_COMPLETED", "1-0")));
    }

    @Test
    void malformedPayloadDoesNotBecomeSuccessfulDisposition() {
        AiNotificationTaskHandler handler =
                new AiNotificationTaskHandler(
                        (u, t, p) -> {
                            fail("invalid payload must not reach the bridge");
                            return DispatchResult.noActiveConnection();
                        });
        assertThrows(
                InvalidInputException.class,
                () -> handler.executeTask("{}", 1L, "AI_TASK_COMPLETED", "1-0"));
    }

    @Test
    void missingNotificationEnvelopeMustNotBeAcknowledgedAsOffline() {
        var handler =
                new AiNotificationTaskHandler(
                        (user, type, payload) -> DispatchResult.noActiveConnection());
        assertThrows(
                InvalidInputException.class,
                () -> handler.executeTask(TASK, null, "AI_TASK_COMPLETED", "1-0"));
        assertThrows(InvalidInputException.class, () -> handler.executeTask(TASK, 1L, "", "1-0"));
    }

    @Test
    void replayIsAnIdempotentStateHintRatherThanAClientDeliveryGuarantee() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiNotificationTaskHandler handler =
                new AiNotificationTaskHandler(
                        (u, t, p) -> {
                            attempts.incrementAndGet();
                            return DispatchResult.noActiveConnection();
                        });
        handler.executeTask(TASK, 1L, "AI_TASK_COMPLETED", "1-0");
        handler.executeTask(TASK, 1L, "AI_TASK_COMPLETED", "2-0");
        assertEquals(2, attempts.get());
    }

    @Test
    void resultCountsCannotClaimUnaccountedConnections() {
        assertThrows(IllegalArgumentException.class, () -> new DispatchResult(2, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DispatchResult(0, -1, 1, 0));
        assertEquals(DispatchResult.Status.REJECTED_FULL, new DispatchResult(2, 0, 1, 1).status());
    }
}
