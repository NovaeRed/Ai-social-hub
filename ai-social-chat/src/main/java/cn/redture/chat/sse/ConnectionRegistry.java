package cn.redture.chat.sse;

import java.util.*;

/** 统一管理连接准入、替换和移除。 */
final class ConnectionRegistry {
    private final Map<Long, Map<String, SseConnection>> users = new HashMap<>();
    private boolean stopped;

    /**
     * 原子注册连接，达到用户上限时移出一条旧连接。
     *
     * @param connection 待注册连接
     * @param limit 每用户连接上限
     * @return 被移出的旧连接，未替换时为 null；调用方负责关闭
     */
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

    /**
     * 按连接对象移除注册记录，并清理空的用户记录。
     *
     * @param connection 待移除连接
     */
    synchronized void remove(SseConnection connection) {
        var context = connection.context();
        var entries = users.get(context.getUserId());
        if (entries != null) {
            entries.remove(context.getClientId(), connection);
            if (entries.isEmpty()) users.remove(context.getUserId());
        }
    }

    /**
     * 获取用户当前注册连接的只读快照。
     *
     * @param userId 用户 ID
     * @return 连接快照，无连接时为空列表
     */
    synchronized List<SseConnection> connections(Long userId) {
        var entries = users.get(userId);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    /**
     * 停止接纳新连接，取得待关闭连接快照。
     *
     * @return 停止准入时仍注册的连接
     */
    synchronized List<SseConnection> stopAdmission() {
        stopped = true;
        return users.values().stream().flatMap(map -> map.values().stream()).toList();
    }

    /**
     * 汇总所有用户的注册连接数。
     *
     * @return 当前注册连接总数
     */
    synchronized int size() {
        return users.values().stream().mapToInt(Map::size).sum();
    }
}
