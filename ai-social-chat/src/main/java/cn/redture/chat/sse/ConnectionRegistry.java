package cn.redture.chat.sse;

import java.util.*;

/** Owns admission and identity-based removal. Never closes a transport while holding its lock. */
final class ConnectionRegistry {
    private final Map<Long, Map<String, SseConnection>> users = new HashMap<>();
    private boolean stopped;

    synchronized SseConnection register(SseConnection connection, int limit) {
        if (stopped) throw new IllegalStateException("SSE service is stopping");
        var contexts =
                users.computeIfAbsent(connection.context().getUserId(), ignored -> new HashMap<>());
        SseConnection victim = null;
        if (contexts.size() >= limit) {
            victim =
                    contexts.values().stream()
                            .min(
                                    Comparator.comparingLong(
                                                    (SseConnection c) ->
                                                            c.context().getLastActivityTime().get())
                                            .thenComparing(
                                                    Comparator.comparingInt(
                                                                    (SseConnection c) ->
                                                                            c.context()
                                                                                    .getQueueSize())
                                                            .reversed())
                                            .thenComparingLong(c -> c.context().getCreateTime()))
                            .orElseThrow();
            contexts.remove(victim.context().getClientId());
        }
        contexts.put(connection.context().getClientId(), connection);
        return victim;
    }

    synchronized void remove(SseConnection connection) {
        var context = connection.context();
        var entries = users.get(context.getUserId());
        if (entries != null) {
            entries.remove(context.getClientId(), connection);
            if (entries.isEmpty()) users.remove(context.getUserId());
        }
    }

    synchronized List<SseConnection> connections(Long userId) {
        var entries = users.get(userId);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    synchronized List<SseConnection> stopAdmission() {
        stopped = true;
        return users.values().stream().flatMap(map -> map.values().stream()).toList();
    }

    synchronized int size() {
        return users.values().stream().mapToInt(Map::size).sum();
    }
}
