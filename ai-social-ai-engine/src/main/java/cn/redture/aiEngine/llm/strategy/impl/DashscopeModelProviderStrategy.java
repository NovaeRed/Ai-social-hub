package cn.redture.aiEngine.llm.strategy.impl;

import cn.redture.aiEngine.llm.config.AiProviderProperties;
import cn.redture.aiEngine.llm.config.ModelCatalog;
import cn.redture.aiEngine.llm.core.execution.ModelExecutionContext;
import cn.redture.aiEngine.llm.strategy.ModelProvider;
import cn.redture.aiEngine.llm.strategy.ModelProviderStrategy;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.aiEngine.llm.tool.AiToolRegistry;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope 供应商策略实现
 */
@Slf4j
@Component
@ModelProvider("dashscope")
@RequiredArgsConstructor
public class DashscopeModelProviderStrategy implements ModelProviderStrategy {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ModelCatalog modelCatalog;
    private final AiToolRegistry aiToolRegistry;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String providerCode() {
        return "dashscope";
    }

    @Override
    public Flux<String> stream(String prompt, ModelExecutionContext context) {
        ResolvedModelConfig config = resolveConfig(context);
        return Flux.<String>create(sink -> {
            String endpoint = normalizeChatCompletionsUrl(config.chatCompletionsUrl());
            String payload = buildPayload(config.modelName(), prompt, true);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + config.apiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(payload, JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    sink.error(buildUpstreamException(response, "DashScope 流式调用失败"));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    sink.error(new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 流式响应体为空", ErrorCodes.UPSTREAM_UNAVAILABLE));
                    return;
                }

                BufferedSource source = body.source();
                while (!source.exhausted() && !sink.isCancelled()) {
                    String line = source.readUtf8Line();
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        sink.complete();
                        return;
                    }

                    String chunk = extractDeltaContent(data);
                    if (!chunk.isEmpty()) {
                        sink.next(chunk);
                    }
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 流式调用异常: " + e.getMessage(), ErrorCodes.UPSTREAM_UNAVAILABLE));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String call(String prompt, ModelExecutionContext context) {
        ResolvedModelConfig config = resolveConfig(context);
        String endpoint = normalizeChatCompletionsUrl(config.chatCompletionsUrl());
        String payload = buildPayload(config.modelName(), prompt, false);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + config.apiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(payload, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildUpstreamException(response, "DashScope 同步调用失败");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 响应体为空", ErrorCodes.UPSTREAM_UNAVAILABLE);
            }
            return extractContent(body.string());
        } catch (IOException e) {
            throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 调用异常: " + e.getMessage(), ErrorCodes.UPSTREAM_UNAVAILABLE);
        }
    }

    @Override
    public String callWithTools(String prompt, ModelExecutionContext context) {
        if (!aiToolRegistry.hasTools()) {
            return call(prompt, context);
        }

        ResolvedModelConfig config = resolveConfig(context);
        String endpoint = normalizeChatCompletionsUrl(config.chatCompletionsUrl());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage(prompt));

        String lastAssistantContent = "";
        for (int round = 0; round < 3; round++) {
            String payload = buildToolPayload(config.modelName(), messages);
            String body = postJson(endpoint, config.apiKey(), payload, "DashScope 工具调用失败");

            JsonNode messageNode = extractMessageNode(body);
            if (messageNode == null || messageNode.isMissingNode()) {
                return "";
            }

            String content = messageNode.path("content").asText("");
            JsonNode toolCallsNode = messageNode.path("tool_calls");

            if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
                return content;
            }

            lastAssistantContent = content;
            messages.add(assistantMessage(messageNode));

            for (JsonNode toolCall : toolCallsNode) {
                String toolCallId = toolCall.path("id").asText("");
                JsonNode functionNode = toolCall.path("function");
                String toolName = functionNode.path("name").asText("");
                String arguments = functionNode.path("arguments").asText("{}");
                String toolResult = aiToolRegistry.executeTool(toolName, arguments);
                messages.add(toolMessage(toolCallId, toolName, toolResult));
            }
        }

        return lastAssistantContent;
    }

    private ResolvedModelConfig resolveConfig(ModelExecutionContext context) {
        if (context == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型执行上下文不能为空", ErrorCodes.MODEL_OPTION_INVALID);
        }

        String resolvedModel = ModelProviderUtil.normalizeModelName(context.modelName());
        if (resolvedModel.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型名称不能为空，请检查模型路由配置", ErrorCodes.MODEL_OPTION_INVALID);
        }

        String resolvedProvider = ModelProviderUtil.normalizeProvider(context.provider());
        if (resolvedProvider.isBlank()) {
            resolvedProvider = providerCode();
        }

        AiProviderProperties.ProviderConfig providerConfig = modelCatalog.findEnabledProviderConfig(resolvedProvider);
        if (providerConfig == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "未找到 provider 配置: provider=" + resolvedProvider, ErrorCodes.MODEL_OPTION_INVALID);
        }

        if (modelCatalog.findEnabledCandidateByProviderAndModel(resolvedProvider, resolvedModel) == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "未找到候选模型配置: provider=" + resolvedProvider + ", modelName=" + resolvedModel, ErrorCodes.MODEL_OPTION_INVALID);
        }

        String apiKey = safe(providerConfig.getApiKey());
        if (apiKey.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型 " + resolvedModel + " 缺少 DashScope apiKey 配置", ErrorCodes.MODEL_OPTION_INVALID);
        }

        String chatCompletionsUrl = buildChatCompletionsUrl(providerConfig);
        if (chatCompletionsUrl.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型 " + resolvedModel + " 缺少 DashScope chat endpoint 配置", ErrorCodes.MODEL_OPTION_INVALID);
        }
        return new ResolvedModelConfig(resolvedProvider, resolvedModel, apiKey, chatCompletionsUrl);
    }

    private String buildChatCompletionsUrl(AiProviderProperties.ProviderConfig providerConfig) {
        String baseUrl = safe(providerConfig.getUrl());
        if (baseUrl.isBlank()) {
            return "";
        }

        Map<String, String> endpoints = providerConfig.getEndpoints();
        String chatPath = endpoints == null ? "" : safe(endpoints.get("chat"));
        if (chatPath.isBlank()) {
            return "";
        }

        if (!baseUrl.endsWith("/") && !chatPath.startsWith("/")) {
            return baseUrl + "/" + chatPath;
        }
        if (baseUrl.endsWith("/") && chatPath.startsWith("/")) {
            return baseUrl + chatPath.substring(1);
        }
        return baseUrl + chatPath;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildPayload(String modelName, String prompt, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        payload.put("messages", List.of(message));
        payload.put("stream", stream);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR, "构造 DashScope 请求体失败", ErrorCodes.INTERNAL_ERROR);
        }
    }

    private String buildToolPayload(String modelName, List<Map<String, Object>> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("tool_choice", "auto");
        payload.put("tools", aiToolRegistry.listToolDefinitions());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR, "构造 DashScope 工具调用请求体失败", ErrorCodes.INTERNAL_ERROR);
        }
    }

    private Map<String, Object> userMessage(String prompt) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        return message;
    }

    private Map<String, Object> assistantMessage(JsonNode messageNode) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", messageNode.path("content").asText(""));

        JsonNode toolCalls = messageNode.path("tool_calls");
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            message.put("tool_calls", objectMapper.convertValue(toolCalls, List.class));
        }
        return message;
    }

    private Map<String, Object> toolMessage(String toolCallId, String name, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("name", name);
        message.put("content", content);
        return message;
    }

    private JsonNode extractMessageNode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            return choices.get(0).path("message");
        } catch (Exception e) {
            log.warn("解析 DashScope 工具调用响应失败", e);
            return null;
        }
    }

    private String postJson(String endpoint, String apiKey, String payload, String failPrefix) {
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(payload, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildUpstreamException(response, failPrefix);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 响应体为空", ErrorCodes.UPSTREAM_UNAVAILABLE);
            }
            return body.string();
        } catch (IOException e) {
            throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "DashScope 调用异常: " + e.getMessage(), ErrorCodes.UPSTREAM_UNAVAILABLE);
        }
    }

    private String normalizeChatCompletionsUrl(String chatCompletionsUrl) {
        String normalized = chatCompletionsUrl == null ? "" : chatCompletionsUrl.trim();
        if (normalized.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "DashScope chatCompletionsUrl 不能为空", ErrorCodes.MODEL_OPTION_INVALID);
        }
        return normalized;
    }

    private String extractContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("解析 DashScope 同步响应失败", e);
            return "";
        }
    }

    private String extractDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            return choices.get(0).path("delta").path("content").asText("");
        } catch (Exception e) {
            log.debug("解析 DashScope 流式分片失败: {}", data, e);
            return "";
        }
    }

    private BaseException buildUpstreamException(Response response, String prefix) {
        String detail = "";
        try {
            ResponseBody body = response.body();
            if (body != null) {
                detail = body.string();
            }
        } catch (Exception ignored) {
            // ignore
        }

        return new BaseException(HttpStatus.SERVICE_UNAVAILABLE, prefix + ": httpStatus=" + response.code() + ", body=" + detail, ErrorCodes.UPSTREAM_UNAVAILABLE);
    }

    private record ResolvedModelConfig(String provider, String modelName, String apiKey, String chatCompletionsUrl) {
    }
}
