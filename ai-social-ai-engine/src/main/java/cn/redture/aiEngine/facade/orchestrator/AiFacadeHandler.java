package cn.redture.aiEngine.facade.orchestrator;

import cn.redture.aiEngine.llm.core.execution.ModelProviderExecutor;
import cn.redture.aiEngine.llm.core.routing.ModelSelector;
import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

@Slf4j
@Component
public class AiFacadeHandler {

    private final ModelSelector modelModelSelector;
    private final ModelProviderExecutor modelProviderExecutor;
    private final Map<String, String> promptTemplates = new ConcurrentHashMap<>();

    public AiFacadeHandler(ModelSelector modelModelSelector,
                           ModelProviderExecutor modelProviderExecutor) {
        this.modelModelSelector = modelModelSelector;
        this.modelProviderExecutor = modelProviderExecutor;
    }

    @PostConstruct
    public void init() {
        loadPrompt("polish.txt", "POLISH");
        loadPrompt("schedule_extraction.txt", "SCHEDULE_EXTRACTION");
        loadPrompt("translation.txt", "TRANSLATION");
        loadPrompt("translation_domain.txt", "TRANSLATION_DOMAIN");
        loadPrompt("smart_reply.txt", "SMART_REPLY");
        loadPrompt("chat_summary.txt", "CHAT_SUMMARY");
        loadPrompt("persona_analysis.txt", "PERSONA_ANALYSIS");
    }

    private void loadPrompt(String filename, String key) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + filename);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            promptTemplates.put(key, content);
        } catch (Exception e) {
            log.error("Failed to load prompt template: " + filename, e);
        }
    }

    public Flux<String> executeTaskStream(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        log.debug("Execute stream task: userId={}, taskType={}, provider={}, model={}", userId, taskType, route.resolvedProvider(), route.resolvedModelName());
        String prompt = buildPrompt(taskType, params);
        return modelProviderExecutor.stream(route, prompt);
    }

    public String executeTask(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        log.debug("Execute task: userId={}, taskType={}, provider={}, model={}", userId, taskType, route.resolvedProvider(), route.resolvedModelName());
        String prompt = buildPrompt(taskType, params);
        return modelProviderExecutor.call(route, prompt);
    }

    public String executeTaskWithTools(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        log.debug("Execute task with tools: userId={}, taskType={}, provider={}, model={}", userId, taskType, route.resolvedProvider(), route.resolvedModelName());
        String prompt = buildPrompt(taskType, params);
        return modelProviderExecutor.callWithTools(route, prompt);
    }

    public ModelRouteDecision resolveModelRoute(AiTaskType taskType, Map<String, Object> params) {
        return modelModelSelector.resolveModelRoute(taskType, params);
    }

    public ModelRouteDecision resolveSystemDefaultRoute(AiTaskType taskType) {
        return modelModelSelector.resolveSystemDefaultRoute(taskType);
    }

    public String buildPrompt(AiTaskType taskType, Map<String, Object> params) {
        if (taskType == AiTaskType.SPEECH_TO_TEXT) return null;

        String templateKey = taskType.name();

        if (taskType == AiTaskType.TRANSLATION) {
            String domain = (String) params.get("domain");
            if (domain != null && !domain.trim().isEmpty()) {
                templateKey = "TRANSLATION_DOMAIN";
            }
        } else if (taskType == AiTaskType.SMART_REPLY) {
            String historyStr = params.get("conversation_history") != null ? JsonUtil.toJson(params.get("conversation_history")) : "无";
            params.put("historyStr", historyStr);
            String profileHint = "";
            if (params.get("user_profile") != null) {
                SmartReplyRequest.UserProfile profile = (SmartReplyRequest.UserProfile) params.get("user_profile");
                String name = profile.getName() != null ? profile.getName() : "用户";
                String role = profile.getRole() != null ? profile.getRole() : "未知身份";
                String style = profile.getCommunicationStyle() != null ? profile.getCommunicationStyle() : "无特殊风格";
                profileHint = "你是" + name + "，身份是" + role + "，沟通风格是" + style + "。";
            } else {
                profileHint = "你是用户本人，沟通风格自然得体。";
            }
            params.put("profileHint", profileHint);
        } else if (taskType == AiTaskType.CHAT_SUMMARY) {
            String summaryType = (String) params.getOrDefault("summary_type", "general");
            String targetLength = (String) params.getOrDefault("target_length", "medium");
            Object keywordsObj = params.get("keywords");
            String keywords = keywordsObj != null ? keywordsObj.toString() : "无";
            params.put("summary_type", summaryType);
            params.put("target_length", targetLength);
            params.put("keywords", keywords);
        } else if (taskType == AiTaskType.SCHEDULE_EXTRACTION || taskType == AiTaskType.PERSONA_ANALYSIS) {
            Object msgObj = params.get("messages");
            if (msgObj != null) {
                params.put("messages", msgObj.toString());
            }
        }

        String template = promptTemplates.getOrDefault(templateKey, "");
        return renderTemplate(template, params);
    }

    private String renderTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty() || params == null) return template;
        String rendered = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            String key = "\\$\\{" + entry.getKey() + "\\}";
            String value = Matcher.quoteReplacement(entry.getValue().toString());
            rendered = rendered.replaceAll(key, value);
        }
        return rendered;
    }
}