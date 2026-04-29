package cn.redture.aiEngine.service.agent;

import cn.redture.aiEngine.facade.orchestrator.AiFacadeHandler;
import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话级 Agent 记忆服务。
 * 使用 Redis 维护每个用户-会话的双层记忆：
 * 1) 近期窗口：保留原始轮次（短期、可追溯）
 * 2) 归档摘要：超出窗口/超长内容压缩后沉淀（长期、抗遗忘）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationAgentMemoryService {

    private static final String MEMORY_ARCHIVE_KEY_PREFIX = "ai:agent:memory:archive:v1:";
    private static final String MEMORY_RECENT_STREAM_KEY_PREFIX = "ai:agent:memory:recent:stream:v1:";
    private static final String MEMORY_LAST_ARCHIVE_AT_KEY_PREFIX = "ai:agent:memory:archive:last_at:v1:";
    private static final Duration MEMORY_TTL = Duration.ofDays(7);
    private static final int RECENT_WINDOW_DAYS = 7;
    private static final int RECENT_MAX_TURNS = 80;
    private static final int RECENT_HARD_MAXLEN = 100;
    private static final int SINGLE_INPUT_MAX_CHARS = 500;
    private static final int ARCHIVE_MAX_CHARS = 2000;
    private static final int FALLBACK_MAX_SENTENCES = 3;
    private static final Duration ARCHIVE_COOLDOWN = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final AiFacadeHandler aiFacadeHandler;

    public String getMemory(Long userId, String conversationPublicId) {
        if (userId == null || conversationPublicId == null || conversationPublicId.isBlank()) {
            return "";
        }
        try {
            String archive = stringRedisTemplate.opsForValue().get(buildArchiveKey(userId, conversationPublicId));
            List<TurnEntry> recentEntries = loadRecentEntries(userId, conversationPublicId, RECENT_MAX_TURNS);
            String recentWindow = renderRecentWindow(recentEntries);

            StringBuilder merged = new StringBuilder();
            if (archive != null && !archive.isBlank()) {
                merged.append("[长期记忆摘要]\n").append(archive.trim()).append("\n\n");
            }
            if (!recentWindow.isBlank()) {
                merged.append("[近期会话窗口]").append("\n").append(recentWindow);
            }
            return merged.toString().trim();
        } catch (Exception e) {
            log.warn("读取 Agent 会话记忆失败, userId={}, conversationPublicId={}", userId, conversationPublicId, e);
            return "";
        }
    }

    public void updateMemory(Long userId,
                             String conversationPublicId,
                             Object conversationHistory,
                             String latestUserInput,
                             String latestAssistantOutput) {
        if (userId == null || conversationPublicId == null || conversationPublicId.isBlank()) {
            return;
        }

        try {
            TurnEntry turn = buildTurnEntry(conversationHistory, latestUserInput, latestAssistantOutput);
            appendRecentTurn(userId, conversationPublicId, turn);

            if (shouldArchive(userId, conversationPublicId)) {
                archiveWindow(userId, conversationPublicId);
            }
            refreshTtl(userId, conversationPublicId);
        } catch (Exception e) {
            log.warn("更新 Agent 会话记忆失败, userId={}, conversationPublicId={}", userId, conversationPublicId, e);
        }
    }

    private boolean shouldArchive(Long userId, String conversationPublicId) {
        String streamKey = buildRecentStreamKey(userId, conversationPublicId);
        Long streamSize = stringRedisTemplate.opsForStream().size(streamKey);
        if (streamSize == null || streamSize <= RECENT_MAX_TURNS) {
            return false;
        }

        String lastArchiveAtStr = stringRedisTemplate.opsForValue().get(buildLastArchiveAtKey(userId, conversationPublicId));
        if (lastArchiveAtStr == null || lastArchiveAtStr.isBlank()) {
            return true;
        }

        try {
            OffsetDateTime lastArchiveAt = OffsetDateTime.parse(lastArchiveAtStr);
            return Duration.between(lastArchiveAt, OffsetDateTime.now()).compareTo(ARCHIVE_COOLDOWN) >= 0;
        } catch (Exception e) {
            return true;
        }
    }

    private void archiveWindow(Long userId, String conversationPublicId) {
        List<TurnEntry> all = loadRecentEntries(userId, conversationPublicId, RECENT_HARD_MAXLEN);
        if (all.isEmpty() || all.size() <= RECENT_MAX_TURNS) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RECENT_WINDOW_DAYS);
        List<TurnEntry> toArchive = new ArrayList<>();

        for (TurnEntry entry : all) {
            if (entry.at().isBefore(cutoff)) {
                toArchive.add(entry);
            }
        }

        if (toArchive.isEmpty()) {
            int overflow = all.size() - RECENT_MAX_TURNS;
            if (overflow > 0) {
                toArchive.addAll(all.subList(0, overflow));
            }
        }

        if (toArchive.isEmpty()) {
            return;
        }

        String archiveKey = buildArchiveKey(userId, conversationPublicId);
        String existingArchive = stringRedisTemplate.opsForValue().get(archiveKey);
        String archiveInput = renderTurnsForArchive(toArchive);
        String mergedArchive = compressArchiveByLlm(userId, existingArchive, archiveInput);
        String boundedArchive = boundArchive(mergedArchive);

        if (!boundedArchive.isBlank()) {
            stringRedisTemplate.opsForValue().set(archiveKey, boundedArchive, MEMORY_TTL);
        }
        stringRedisTemplate.opsForValue().set(buildLastArchiveAtKey(userId, conversationPublicId), OffsetDateTime.now().toString(), MEMORY_TTL);
    }

    private String compressArchiveByLlm(Long userId, String existingArchive, String newArchiveInput) {
        String baseArchive = existingArchive == null ? "" : existingArchive.trim();
        String incremental = newArchiveInput == null ? "" : newArchiveInput.trim();

        if (incremental.isBlank()) {
            return baseArchive;
        }

        String content = "已有长期摘要:\n" + (baseArchive.isBlank() ? "无" : baseArchive)
                + "\n\n新增待归档内容:\n" + incremental;

        try {
            Map<String, Object> params = Map.of(
                    "content", content,
                    "summary_type", "agent_memory",
                    "target_length", "short",
                    "keywords", List.of("长期偏好", "沟通风格", "稳定关系状态", "未完成事项", "关键事实")
            );
            ModelRouteDecision route = aiFacadeHandler.resolveSystemDefaultRoute(AiTaskType.CHAT_SUMMARY);
            String summary = aiFacadeHandler.executeTask(userId, AiTaskType.CHAT_SUMMARY, params, route);
            String cleaned = summary == null ? "" : summary.trim();
            if (cleaned.isBlank()) {
                return fallbackArchive(baseArchive, incremental);
            }
            String wrapped = "[memory_updated_at=" + OffsetDateTime.now() + "]\n" + cleaned;
            return boundArchive(wrapped);
        } catch (Exception e) {
            log.warn("压缩 Agent 会话归档失败，降级为关键句保留", e);
            return fallbackArchive(baseArchive, incremental);
        }
    }

    private TurnEntry buildTurnEntry(Object conversationHistory, String latestUserInput, String latestAssistantOutput) {
        String historyText = trimForInput(conversationHistory == null ? "" : toJsonSafe(conversationHistory), SINGLE_INPUT_MAX_CHARS);
        String userInput = trimForInput(latestUserInput == null ? "" : latestUserInput.trim(), SINGLE_INPUT_MAX_CHARS);
        String assistantOutput = trimForInput(latestAssistantOutput == null ? "" : latestAssistantOutput.trim(), SINGLE_INPUT_MAX_CHARS);

        StringBuilder sb = new StringBuilder();
        if (!historyText.isBlank()) {
            sb.append("history_tail=").append(historyText).append("\n");
        }
        if (!userInput.isBlank()) {
            sb.append("latest_user_input=").append(userInput).append("\n");
        }
        if (!assistantOutput.isBlank()) {
            sb.append("latest_assistant_output=").append(assistantOutput).append("\n");
        }
        return new TurnEntry(OffsetDateTime.now(), sb.toString().trim());
    }

    private void appendRecentTurn(Long userId, String conversationPublicId, TurnEntry entry) {
        String key = buildRecentStreamKey(userId, conversationPublicId);
        Map<String, String> payload = Map.of(
                "at", entry.at().toString(),
                "content", entry.content(),
                "id", UUID.randomUUID().toString()
        );
        stringRedisTemplate.opsForStream().add(StreamRecords.string(payload).withStreamKey(key));
        stringRedisTemplate.opsForStream().trim(key, RECENT_HARD_MAXLEN, true);
    }

    private List<TurnEntry> loadRecentEntries(Long userId, String conversationPublicId, int limit) {
        String key = buildRecentStreamKey(userId, conversationPublicId);
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(key, Range.unbounded())
                .stream()
                .limit(Math.max(limit, 1))
                .toList();
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<TurnEntry> entries = new ArrayList<>();
        Collections.reverse(records);
        for (MapRecord<String, Object, Object> record : records) {
            try {
                Map<Object, Object> payload = record.getValue();
                String atStr = payload.get("at") == null ? null : String.valueOf(payload.get("at"));
                String content = payload.get("content") == null ? "" : String.valueOf(payload.get("content"));
                OffsetDateTime at = atStr == null || atStr.isBlank() ? OffsetDateTime.now() : OffsetDateTime.parse(atStr);
                if (!content.isBlank()) {
                    entries.add(new TurnEntry(at, content));
                }
            } catch (Exception ignore) {
                // 忽略单条坏数据，继续加载剩余内容。
            }
        }
        return entries;
    }

    private String renderRecentWindow(List<TurnEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RECENT_WINDOW_DAYS);
        StringBuilder sb = new StringBuilder();
        for (TurnEntry entry : entries) {
            if (entry.at().isBefore(cutoff)) {
                continue;
            }
            sb.append("[turn@").append(entry.at()).append("]\n");
            sb.append(entry.content()).append("\n");
        }
        return sb.toString().trim();
    }

    private String renderTurnsForArchive(List<TurnEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TurnEntry entry : entries) {
            sb.append("[turn@").append(entry.at()).append("] ");
            sb.append(entry.content()).append("\n");
        }
        return sb.toString().trim();
    }

    private int totalChars(List<TurnEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (TurnEntry entry : entries) {
            total += entry.content() == null ? 0 : entry.content().length();
        }
        return total;
    }

    private String fallbackArchive(String baseArchive, String incremental) {
        String keyLines = extractKeySentences(incremental, FALLBACK_MAX_SENTENCES);
        String merged = baseArchive.isBlank() ? keyLines : (baseArchive + "\n[补充关键句]\n" + keyLines);
        return boundArchive(merged);
    }

    private String extractKeySentences(String text, int maxSentences) {
        if (text == null || text.isBlank() || maxSentences <= 0) {
            return "";
        }
        String[] parts = text.split("[。！？.!?\\n]+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.length() > 120) {
                trimmed = trimmed.substring(0, 120);
            }
            if (count > 0) {
                sb.append("\n");
            }
            sb.append("- ").append(trimmed);
            count++;
            if (count >= maxSentences) {
                break;
            }
        }
        return sb.toString();
    }

    private String boundArchive(String archive) {
        if (archive == null) {
            return "";
        }
        String normalized = archive.trim();
        if (normalized.length() <= ARCHIVE_MAX_CHARS) {
            return normalized;
        }
        return normalized.substring(0, ARCHIVE_MAX_CHARS);
    }

    private String trimForInput(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
    }

    private String toJsonSafe(Object source) {
        try {
            return JsonUtil.toJson(source);
        } catch (Exception e) {
            return String.valueOf(source);
        }
    }

    private void refreshTtl(Long userId, String conversationPublicId) {
        String archiveKey = buildArchiveKey(userId, conversationPublicId);
        String recentKey = buildRecentStreamKey(userId, conversationPublicId);
        String lastArchiveAtKey = buildLastArchiveAtKey(userId, conversationPublicId);
        stringRedisTemplate.expire(archiveKey, MEMORY_TTL);
        stringRedisTemplate.expire(recentKey, MEMORY_TTL);
        stringRedisTemplate.expire(lastArchiveAtKey, MEMORY_TTL);
    }

    private String buildArchiveKey(Long userId, String conversationPublicId) {
        return MEMORY_ARCHIVE_KEY_PREFIX + userId + ":" + conversationPublicId;
    }

    private String buildRecentStreamKey(Long userId, String conversationPublicId) {
        return MEMORY_RECENT_STREAM_KEY_PREFIX + userId + ":" + conversationPublicId;
    }

    private String buildLastArchiveAtKey(Long userId, String conversationPublicId) {
        return MEMORY_LAST_ARCHIVE_AT_KEY_PREFIX + userId + ":" + conversationPublicId;
    }

    private record TurnEntry(OffsetDateTime at, String content) {
    }
}