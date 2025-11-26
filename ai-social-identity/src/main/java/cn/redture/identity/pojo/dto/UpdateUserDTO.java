package cn.redture.identity.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 用于更新用户信息的 DTO
 */
@Data
public class UpdateUserDTO {

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    @JsonProperty("ai_analysis_enabled")
    private Boolean aiAnalysisEnabled;
}
