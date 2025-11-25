package cn.redture.identity.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一的认证响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponseDTO {

    /**
     * 访问令牌（Access Token）
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * 刷新令牌（Refresh Token）
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 令牌类型，固定为 Bearer
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Access Token 的有效期，单位：秒
     */
    @JsonProperty("expires_in")
    private long expiresIn;
}

