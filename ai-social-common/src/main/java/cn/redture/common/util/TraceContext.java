package cn.redture.common.util;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * 链路追踪上下文工具类
 */
public class TraceContext {
    private static final String TRACE_ID_KEY = "traceId";

    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}