package cn.redture.common.integration.notification;

/** Connection-level queue admission; this result does not acknowledge client delivery. */
public record DispatchResult(
        int targetConnections, int enqueuedConnections, int rejectedFull, int rejectedClosed) {
    public enum Status {
        NO_ACTIVE_CONNECTION,
        ENQUEUED,
        PARTIALLY_ENQUEUED,
        REJECTED_FULL,
        REJECTED_CLOSED
    }

    public DispatchResult {
        if (targetConnections < 0
                || enqueuedConnections < 0
                || rejectedFull < 0
                || rejectedClosed < 0
                || (long) enqueuedConnections + rejectedFull + rejectedClosed
                        != targetConnections) {
            throw new IllegalArgumentException(
                    "Dispatch counts must be nonnegative and sum to targetConnections");
        }
    }

    public static DispatchResult noActiveConnection() {
        return new DispatchResult(0, 0, 0, 0);
    }

    public Status status() {
        if (targetConnections == 0) return Status.NO_ACTIVE_CONNECTION;
        if (enqueuedConnections == targetConnections) return Status.ENQUEUED;
        if (enqueuedConnections > 0) return Status.PARTIALLY_ENQUEUED;
        return rejectedFull > 0 ? Status.REJECTED_FULL : Status.REJECTED_CLOSED;
    }
}
