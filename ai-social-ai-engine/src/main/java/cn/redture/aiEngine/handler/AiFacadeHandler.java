package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.enums.AiProvider;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import cn.redture.common.util.JsonUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI门面服务实现
 */
@Slf4j
@Component
public class AiFacadeHandler {

    private final ChatClient qwenClient;

    public AiFacadeHandler(@Qualifier("qwenClient") ChatClient qwenClient) {
        this.qwenClient = qwenClient;
    }

    public Flux<String> executeTaskStream(Long userId, AiTaskType taskType, Map<String, Object> params,
                                          AiProvider provider) {
        log.debug("Execute stream task: userId={}, taskType={}, provider={}", userId, taskType, provider);

        String prompt = buildPrompt(taskType, params);
        DashScopeChatOptions options = buildOptions(params);

        return qwenClient.prompt()
                .user(prompt)
                .options(options)
                .stream()
                .content()
                .doOnError(error -> log.error("Error in stream task", error))
                .doOnComplete(() -> log.debug("Stream task completed"));
    }

    public String executeTask(Long userId, AiTaskType taskType, Map<String, Object> params, AiProvider provider) {
        log.debug("Execute task: userId={}, taskType={}, provider={}", userId, taskType, provider);

        String prompt = buildPrompt(taskType, params);
        DashScopeChatOptions options = buildOptions(params);

        return qwenClient.prompt()
                .user(prompt)
                .options(options)
                .call()
                .content();
    }

    @Resource
    private ToolCallback[] toolCallbacks;

    public String executeTaskWithTools(Long userId, AiTaskType taskType, Map<String, Object> params, AiProvider provider) {
        log.debug("Execute task with tools: userId={}, taskType={}, provider={}", userId, taskType, provider);

        String prompt = buildPrompt(taskType, params);
        DashScopeChatOptions options = buildOptions(params);

        return qwenClient.prompt()
                .user(prompt)
                .options(options)
                .toolCallbacks(toolCallbacks)
                .call()
                .content();
    }

    private DashScopeChatOptions buildOptions(Map<String, Object> params) {
        var builder = DashScopeChatOptions.builder();

        if (params.containsKey("override_config")) {
            Object configObj = params.get("override_config");
            if (configObj instanceof ModelConfig config) {
                if (config.getTemperature() != null) {
                    builder.withTemperature(config.getTemperature());
                }
                if (config.getTopP() != null) {
                    builder.withTopP(config.getTopP());
                }
                if (config.getTopK() != null) {
                    builder.withTopK(config.getTopK());
                }
                if (config.getMaxTokens() != null) {
                    builder.withMaxToken(config.getMaxTokens());
                }
                if (config.getStop() != null) {
                    builder.withStop(List.of(config.getStop()));
                }
            } else if (configObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) configObj;
                if (config.containsKey("temperature")) {
                    builder.withTemperature((Double) config.get("temperature"));
                }
                if (config.containsKey("top_p")) {
                    builder.withTopP((Double) config.get("top_p"));
                }
                if (config.containsKey("top_k")) {
                    builder.withTopK(Integer.valueOf(config.get("top_k").toString()));
                }
            }
        }
        return builder.build();
    }

    public String buildPrompt(AiTaskType taskType, Map<String, Object> params) {
        return switch (taskType) {
            case POLISH -> buildPolishPrompt(params);
            case SCHEDULE_EXTRACTION -> buildSchedulePrompt(params);
            case TRANSLATION -> buildTranslationPrompt(params);
            case SMART_REPLY -> buildSmartReplyPrompt(params);
            case SPEECH_TO_TEXT -> null;
            case CHAT_SUMMARY -> buildSummaryPrompt(params);
            case PERSONA_ANALYSIS -> buildPersonaPrompt(params);
        };
    }

    private String buildPolishPrompt(Map<String, Object> params) {
        String message = (String) params.get("message");
        return String.format("""
                请润色以下消息，使其更加专业、友好和得体。只返回润色后的文本，不要添加任何解释。
                
                原文：
                %s
                """, message);
    }

    private String buildSchedulePrompt(Map<String, Object> params) {
        String messages = params.get("messages").toString();
        return String.format("""
                请从以下对话中提取所有日程安排信息，并严格按照指定格式返回一个 JSON 对象，不要包含任何额外说明、注释或文本。
                如果对话中包含相对时间（如“明天”、“下周五”），请调用工具获取当前时间，并计算出具体的日期和时间。
                
                对话内容：
                %s
                
                返回格式（仅输出 JSON，不要任何其他内容）：
                {"schedules":[{"title":"日程标题","time":"yyyy-MM-dd HH:mm:ss","location":"地点（可选）","participants":["参与者（可选）"]}]}
                
                注意：
                1. 必须严格遵守上述 JSON 格式。
                2. 不要返回 Markdown 代码块（如 ```json ... ```），直接返回纯 JSON 字符串。
                """, messages);
    }

    private String buildTranslationPrompt(Map<String, Object> params) {
        String text = (String) params.get("text");
        String targetLanguage = (String) params.get("targetLanguage");
        String domain = (String) params.get("domain");

        if (domain != null && !domain.trim().isEmpty()) {
            return String.format("""
                    请将以下%s领域的文本准确翻译为%s，使用该领域的专业术语。只返回翻译结果，不要添加任何解释、前缀、后缀或额外内容。
                    
                    原文：
                    %s
                    """, domain, targetLanguage, text);
        } else {
            return String.format("""
                    请将以下文本翻译为%s，只返回翻译结果，不要添加任何解释、前缀、后缀或额外内容。
                    
                    原文：
                    %s
                    """, targetLanguage, text);
        }
    }

    private String buildSmartReplyPrompt(Map<String, Object> params) {
        log.debug("Building smart reply prompt with params: {}", params);
        String message = params.get("message").toString();
        String historyStr = params.get("conversation_history") != null ? JsonUtil.toJson(params.get("conversationHistory")) : "无";

        String profileHint = "";
        if (params.get("user_profile") != null) {
            SmartReplyRequest.UserProfile profile = (SmartReplyRequest.UserProfile) params.get("user_profile");
            String name = profile.getName() != null ? profile.getName() : "用户";
            String role = profile.getRole() != null ? profile.getRole() : "未知身份";
            String style = profile.getCommunicationStyle() != null ? profile.getCommunicationStyle() : "无特殊风格";

            profileHint = "你是%s，身份是%s，沟通风格是%s。".formatted(name, role, style);
        } else {
            profileHint = "你是用户本人，沟通风格自然得体。";
        }

        return """
                你正在帮用户生成针对最新消息的智能回复。请以用户本人的口吻，生成简短、得体的建议。
                
                【最新消息（对方发的）】
                %s
                
                【对话历史】
                %s
                
                【你是谁】
                %s
                
                要求：
                - 回复符合你的身份和沟通风格；
                - 自然口语化；
                - 不要编号、前缀或解释。
                """.formatted(message, historyStr, profileHint);
    }

    private String buildSummaryPrompt(Map<String, Object> params) {
        String content = (String) params.get("content");
        String summaryType = (String) params.getOrDefault("summary_type", "general");
        String targetLength = (String) params.getOrDefault("target_length", "medium");
        Object keywordsObj = params.get("keywords");
        String keywords = keywordsObj != null ? keywordsObj.toString() : "无";

        return String.format("""
                请对以下内容进行总结。
                
                总结类型：%s
                目标长度：%s
                关注关键词：%s
                
                内容：
                %s
                
                请直接输出总结结果，不要包含其他废话。
                """, summaryType, targetLength, keywords, content);
    }

    private String buildPersonaPrompt(Map<String, Object> params) {
        String messages = params.get("messages").toString();
        return String.format("""
                你是一个专业的心理分析专家。请根据以下对话内容，分析目标用户的性格特征。
                
                【分析要求】
                1. 必须严格按照指定的 JSON 格式返回结果。
                2. 严禁包含任何 Markdown 标记（如 ```json ... ```）。
                3. 严禁包含任何开场白、结束语、分析过程或解释性文字，只返回纯 JSON 字符串。
                4. 如果无法从对话中推断出某些字段（如兴趣爱好），请返回空数组 []，不要编造。
                5. 确保输出是一个合法的 JSON 字符串，可以直接被解析。
                
                【对话内容】
                %s
                
                【返回格式】
                {
                  "personality": "性格类型（如：外向、内向、理性、感性等，限5个字以内）",
                  "traits": ["特征1", "特征2", "特征3"],
                  "communication_style": "沟通风格描述（限50字以内）",
                  "interests": ["兴趣1", "兴趣2"]
                }
                """, messages);
    }
}
