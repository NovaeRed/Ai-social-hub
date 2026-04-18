package cn.redture.aiEngine.facade.prompt;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * Prompt 模板注册中心。
 */
@Slf4j
@Component
public class PromptTemplateRegistry {

    private static final Map<String, String> TEMPLATE_FILE_MAPPING = Map.ofEntries(
            Map.entry("POLISH", "polish.txt"),
            Map.entry("SCHEDULE_EXTRACTION", "schedule_extraction.txt"),
            Map.entry("TRANSLATION", "translation.txt"),
            Map.entry("TRANSLATION_DOMAIN", "translation_domain.txt"),
            Map.entry("SMART_REPLY", "smart_reply.txt"),
            Map.entry("CHAT_SUMMARY", "chat_summary.txt"),
            Map.entry("PERSONA_ANALYSIS", "persona_analysis.txt"),
            Map.entry("SYSTEM_GUARDRAIL", "system_guardrail.txt")
    );

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    /**
     * 启动时加载全部模板。
     */
    @PostConstruct
    public void init() {
        TEMPLATE_FILE_MAPPING.forEach((key, filename) -> {
            String content = loadTemplateFile(filename);
            templates.put(key, content);
        });
        log.info("Prompt 模板加载完成, count={}", templates.size());
    }

    /**
     * 获取模板内容。
     *
     * @param key 模板键
     * @return 模板内容
     */
    public String getTemplate(String key) {
        String template = templates.get(key);
        if (template == null) {
            throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "缺少 Prompt 模板: " + key,
                    ErrorCodes.INTERNAL_ERROR);
        }
        return template;
    }

    /**
     * 渲染模板占位符。
     *
     * @param template 模板内容
     * @param params 渲染参数
     * @return 渲染结果
     */
    public String renderTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty() || params == null || params.isEmpty()) {
            return template;
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String key = "\\$\\{" + entry.getKey() + "\\}";
            String value = Matcher.quoteReplacement(entry.getValue().toString());
            rendered = rendered.replaceAll(key, value);
        }
        return rendered;
    }

    private String loadTemplateFile(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + filename);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "加载 Prompt 模板失败: " + filename,
                    ErrorCodes.INTERNAL_ERROR);
        }
    }
}
