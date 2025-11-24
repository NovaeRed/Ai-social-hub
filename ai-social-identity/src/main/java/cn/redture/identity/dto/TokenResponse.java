package cn.redture.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class TokenResponse {

    /**
     * 访问令牌（Access Token）
     */
    private String access_token;

    /**
     * 刷新令牌（Refresh Token）
     */
    private String refresh_token;

    /**
     * 令牌类型，固定为 Bearer
     */
    private String token_type;

    /**
     * Access Token 的有效期，单位：秒
     */
    private long expires_in;
}

