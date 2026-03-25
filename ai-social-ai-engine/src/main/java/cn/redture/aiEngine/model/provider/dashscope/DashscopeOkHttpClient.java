package cn.redture.aiEngine.model.provider.dashscope;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope HTTP 客户端（基于 OkHttp）。
 */
@Slf4j
@Component
public class DashscopeOkHttpClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public DashscopeOkHttpClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String call(String chatCompletionsUrl, String apiKey, String modelName, String prompt) {
        String endpoint = normalizeChatCompletionsUrl(chatCompletionsUrl);
        String payload = buildPayload(modelName, prompt, false);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(payload, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildUpstreamException(response, "DashScope 同步调用失败");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE,
                        "DashScope 响应体为空",
                        ErrorCodes.UPSTREAM_UNAVAILABLE);
            }
            return extractContent(body.string());
        } catch (IOException e) {
            throw new BaseException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DashScope 调用异常: " + e.getMessage(),
                    ErrorCodes.UPSTREAM_UNAVAILABLE);
        }
    }

    public Flux<String> stream(String chatCompletionsUrl, String apiKey, String modelName, String prompt) {
        return Flux.<String>create(sink -> {
            String endpoint = normalizeChatCompletionsUrl(chatCompletionsUrl);
            String payload = buildPayload(modelName, prompt, true);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
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

        return new BaseException(HttpStatus.SERVICE_UNAVAILABLE,
                prefix + ": httpStatus=" + response.code() + ", body=" + detail,
                ErrorCodes.UPSTREAM_UNAVAILABLE);
    }
}
