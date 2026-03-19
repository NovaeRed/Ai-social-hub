package cn.redture.aiEngine.pojo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI用户配置参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigParams {
    /**
     * 是否开启自动审核
     */
    @JsonProperty("auto_moderation")
    private Boolean autoModeration;

    /**
     * 是否开启智能回复建议
     */
    @JsonProperty("smart_reply_enabled")
    private Boolean smartReplyEnabled;
}
