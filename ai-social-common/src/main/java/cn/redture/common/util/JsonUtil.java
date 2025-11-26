package cn.redture.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.redture.common.exception.JsonException;
import org.springframework.http.HttpStatus;

/**
 * 简单的 JSON 工具类，基于 Jackson 的 {@link ObjectMapper}。
 */
public class JsonUtil {

    // 全局复用的 ObjectMapper，线程安全
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将对象序列化为 JSON 字符串。
     * 如序列化失败，抛出自定义业务异常
     */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 序列化失败");
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型对象
     */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new JsonException(HttpStatus.BAD_REQUEST, "JSON 解析失败");
        }
    }
}
