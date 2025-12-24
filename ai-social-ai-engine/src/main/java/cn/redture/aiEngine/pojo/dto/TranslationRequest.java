package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 翻译请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TranslationRequest extends BaseAiTaskRequest {

    /**
     * 待翻译文本
     */
    private String text;

    /**
     * 源语言（可选，不填则自动检测）
     */
    @JsonProperty("source_language")
    private String sourceLanguage;

    /**
     * 目标语言
     */
    @JsonProperty("target_language")
    private String targetLanguage;

    /**
     * 专业领域（如法律、医疗等，可选）
     */
    private String domain;
}
