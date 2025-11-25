package cn.redture.identity.service;

import cn.redture.identity.pojo.dto.LoginRequest;
import cn.redture.identity.pojo.dto.RegisterRequest;
import cn.redture.identity.pojo.dto.TokenResponse;

public interface AuthService {

    /**
     * 注册新用户并返回 JWT token。
     */
    TokenResponse register(RegisterRequest request);

    /**
     * 登录并返回 JWT token。
     */
    TokenResponse login(LoginRequest request);

    /**
     * 登出当前用户。
     * @param authorizationHeader 认证请求头
     */
    void logout(String authorizationHeader);

    /**
     * 使用刷新令牌换取新的访问令牌（以及新的刷新令牌）
     */
    TokenResponse refreshToken(String expiredAccessToken, String refreshToken);
}
