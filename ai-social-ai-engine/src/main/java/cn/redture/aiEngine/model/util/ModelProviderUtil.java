package cn.redture.aiEngine.model.util;

import java.util.Locale;

/**
 * 模型工具类
 */
public final class ModelProviderUtil {

    private ModelProviderUtil() {
    }

    public static String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeModelName(String modelName) {
        return modelName == null ? "" : modelName.trim();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
