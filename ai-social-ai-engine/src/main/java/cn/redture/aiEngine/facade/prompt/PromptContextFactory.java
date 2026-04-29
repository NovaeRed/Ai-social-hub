package cn.redture.aiEngine.facade.prompt;

import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import cn.redture.common.util.JsonUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 上下文构建器。
 */
@Component
public class PromptContextFactory {

    /**
     * 构建 Prompt 渲染上下文。
     *
     * @param taskType 任务类型
     * @param rawParams 原始参数
     * @return 渲染上下文
     */
    public PromptBuildContext buildContext(AiTaskType taskType, Map<String, Object> rawParams) {
        if (taskType == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "任务类型不能为空", ErrorCodes.INVALID_INPUT);
        }

        if (taskType == AiTaskType.SPEECH_TO_TEXT) {
            return new PromptBuildContext(null, Map.of(), Map.of());
        }

        Map<String, Object> renderParams = rawParams == null ? new HashMap<>() : new HashMap<>(rawParams);
        String templateKey = taskType.name();

        switch (taskType) {
            case TRANSLATION -> {
                String domain = toPlainText(renderParams.get("domain"));
                if (!domain.isBlank()) {
                    templateKey = "TRANSLATION_DOMAIN";
                }
            }
            case SMART_REPLY -> {
                String historyStr = renderParams.get("conversation_history") != null
                        ? toPlainText(renderParams.get("conversation_history"))
                        : "无";
                renderParams.put("historyStr", historyStr);
                renderParams.put("profileHint", buildProfileHint(renderParams.get("user_profile")));
                renderParams.put("longTermMemory", toDefault(renderParams.get("long_term_memory"), "无"));
            }
            case CHAT_SUMMARY -> {
                String summaryType = toDefault(renderParams.get("summary_type"), "general");
                String targetLength = toDefault(renderParams.get("target_length"), "medium");
                String keywords = toDefault(renderParams.get("keywords"), "无");

                renderParams.put("summary_type", summaryType);
                renderParams.put("target_length", targetLength);
                renderParams.put("keywords", keywords);
                renderParams.put("longTermMemory", toDefault(renderParams.get("long_term_memory"), "无"));
            }
            case SCHEDULE_EXTRACTION, PERSONA_ANALYSIS -> {
                Object messages = renderParams.get("messages");
                if (messages != null) {
                    renderParams.put("messages", toPlainText(messages));
                }
            }
            default -> {
                // no-op
            }
        }

        Map<String, String> riskInputs = buildRiskInputs(taskType, renderParams);
        wrapUserInputBlocks(taskType, renderParams);

        return new PromptBuildContext(templateKey, renderParams, riskInputs);
    }

    private Map<String, String> buildRiskInputs(AiTaskType taskType, Map<String, Object> renderParams) {
        Map<String, String> riskInputs = new LinkedHashMap<>();
        switch (taskType) {
            case POLISH -> putRiskInput(riskInputs, "message", renderParams.get("message"));
            case TRANSLATION -> {
                putRiskInput(riskInputs, "text", renderParams.get("text"));
                putRiskInput(riskInputs, "domain", renderParams.get("domain"));
                putRiskInput(riskInputs, "targetLanguage", renderParams.get("targetLanguage"));
            }
            case SMART_REPLY -> {
                putRiskInput(riskInputs, "message", renderParams.get("message"));
                putRiskInput(riskInputs, "historyStr", renderParams.get("historyStr"));
            }
            case CHAT_SUMMARY -> {
                putRiskInput(riskInputs, "content", renderParams.get("content"));
                putRiskInput(riskInputs, "keywords", renderParams.get("keywords"));
            }
            case SCHEDULE_EXTRACTION, PERSONA_ANALYSIS -> putRiskInput(riskInputs, "messages", renderParams.get("messages"));
            default -> {
                // no-op
            }
        }
        return riskInputs;
    }

    private void wrapUserInputBlocks(AiTaskType taskType, Map<String, Object> renderParams) {
        switch (taskType) {
            case POLISH -> wrapValue(renderParams, "message");
            case TRANSLATION -> wrapValue(renderParams, "text");
            case SMART_REPLY -> {
                wrapValue(renderParams, "message");
                wrapValue(renderParams, "historyStr");
            }
            case CHAT_SUMMARY -> {
                wrapValue(renderParams, "content");
                wrapValue(renderParams, "keywords");
            }
            case SCHEDULE_EXTRACTION, PERSONA_ANALYSIS -> wrapValue(renderParams, "messages");
            default -> {
                // no-op
            }
        }
    }

    private void putRiskInput(Map<String, String> inputs, String key, Object value) {
        String text = toPlainText(value);
        if (!text.isBlank()) {
            inputs.put(key, text);
        }
    }

    private void wrapValue(Map<String, Object> renderParams, String key) {
        Object value = renderParams.get(key);
        if (value == null) {
            return;
        }

        String text = toPlainText(value);
        renderParams.put(key, "<user_input>\n" + text + "\n</user_input>");
    }

    private String buildProfileHint(Object profileObj) {
        if (profileObj instanceof SmartReplyRequest.UserProfile profile) {
            String name = toDefault(profile.getName(), "用户");
            String role = toDefault(profile.getRole(), "未知身份");
            String style = toDefault(profile.getCommunicationStyle(), "无特殊风格");
            return "你是" + name + "，身份是" + role + "，沟通风格是" + style + "。";
        }

        if (profileObj instanceof Map<?, ?> map) {
            String name = toDefault(map.get("name"), "用户");
            String role = toDefault(map.get("role"), "未知身份");
            String style = toDefault(map.get("communicationStyle"), "无特殊风格");
            return "你是" + name + "，身份是" + role + "，沟通风格是" + style + "。";
        }

        return "你是用户本人，沟通风格自然得体。";
    }

    private String toDefault(Object value, String defaultValue) {
        String text = toPlainText(value);
        return text.isBlank() ? defaultValue : text;
    }

    private String toPlainText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            return JsonUtil.toJson(value);
        } catch (Exception ignored) {
            return value.toString();
        }
    }

    /**
     * Prompt 构建上下文。
     *
     * @param templateKey 模板键
     * @param renderParams 渲染参数
     * @param riskInputs 风控输入文本
     */
    public record PromptBuildContext(String templateKey,
                                     Map<String, Object> renderParams,
                                     Map<String, String> riskInputs) {
    }
}
