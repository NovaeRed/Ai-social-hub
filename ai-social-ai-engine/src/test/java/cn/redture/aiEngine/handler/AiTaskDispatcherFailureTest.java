package cn.redture.aiEngine.handler;

import static org.junit.jupiter.api.Assertions.*;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.common.exception.BaseException;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

class AiTaskDispatcherFailureTest {
    @Test
    void runtimeFailureReachesConsumerInsteadOfBeingAcknowledged() {
        RuntimeException failure = new RuntimeException("notification bridge unavailable");
        assertSame(
                failure,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                dispatcher(failure)
                                        .dispatch(
                                                "NOTIFICATION_TASK",
                                                "AI_TASK_COMPLETED",
                                                1L,
                                                "{}",
                                                "1-0")));
    }

    @Test
    void businessErrorRetainsItsRetryClassification() {
        BaseException failure =
                new BaseException(503, "temporarily unavailable", "UPSTREAM_UNAVAILABLE");
        assertSame(
                failure,
                assertThrows(
                        BaseException.class,
                        () ->
                                dispatcher(failure)
                                        .dispatch(
                                                "NOTIFICATION_TASK",
                                                "AI_TASK_COMPLETED",
                                                1L,
                                                "{}",
                                                "1-0")));
    }

    @Test
    void checkedFailureRetainsItsCause() {
        IOException failure = new IOException("bridge unavailable");
        RuntimeException propagated =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                dispatcher(failure)
                                        .dispatch(
                                                "NOTIFICATION_TASK",
                                                "AI_TASK_COMPLETED",
                                                1L,
                                                "{}",
                                                "1-0"));
        assertSame(failure, propagated.getCause());
    }

    private AiTaskDispatcher dispatcher(Exception failure) {
        return new AiTaskDispatcher(
                List.of(
                        new AiTaskHandler() {
                            public AsyncTaskDomain getDomain() {
                                return AsyncTaskDomain.NOTIFICATION_TASK;
                            }

                            public void executeTask(
                                    String task, Long user, String event, String record)
                                    throws Exception {
                                throw failure;
                            }
                        }));
    }
}
