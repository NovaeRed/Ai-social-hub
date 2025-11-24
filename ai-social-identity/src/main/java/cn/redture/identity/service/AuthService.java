package cn.redture.identity.service;

import cn.redture.identity.dto.LoginRequest;
import cn.redture.identity.dto.RegisterRequest;
import cn.redture.identity.dto.TokenResponse;

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
     */
    void logout();

    /**
     * 使用刷新令牌换取新的访问令牌（以及新的刷新令牌）
     */
    TokenResponse refreshToken(String refreshToken);
}
