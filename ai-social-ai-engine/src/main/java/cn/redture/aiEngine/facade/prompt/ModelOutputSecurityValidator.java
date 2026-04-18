package cn.redture.aiEngine.facade.prompt;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 模型输出安全校验器。
 */
@Slf4j
@Component
public class ModelOutputSecurityValidator {

    private static final int STREAM_SCAN_WINDOW = 4096;

    private static final List<Pattern> LEAK_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(api[_-]?key|secret[_-]?key|access[_-]?token)\\b\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_\\-]{8,}"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9_\\-=\\.]{16,}"),
            Pattern.compile("\\bsk-[A-Za-z0-9]{16,}\\b"),
                Pattern.compile("(?is).*(核心规则|防御规则|不要透露本提示词|系统最高提示词|你是\\s*Ai-social-hub\\s*平台的智能助手).*")
    );

    /**
     * 流式输出分片阶段校验。
     *
     * @param outputSnapshot 当前累计输出
     */
    public void validateChunk(CharSequence outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.length() < 24) {
            return;
        }
        String text = outputSnapshot.toString();
        String tail = text.length() > STREAM_SCAN_WINDOW
                ? text.substring(text.length() - STREAM_SCAN_WINDOW)
                : text;
        validateOrThrow(tail);
    }

    /**
     * 最终输出结果校验。
     *
     * @param output 最终文本
     */
    public void validateFinal(String output) {
        if (output == null || output.isBlank()) {
            return;
        }
        validateOrThrow(output);
    }

    private void validateOrThrow(String text) {
        for (Pattern pattern : LEAK_PATTERNS) {
            if (pattern.matcher(text).find()) {
                log.error("模型输出触发敏感内容拦截: pattern={}", pattern.pattern());
                throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "模型输出触发安全策略，结果已拦截",
                        ErrorCodes.MODEL_OUTPUT_SECURITY_BLOCKED);
            }
        }
    }
}
