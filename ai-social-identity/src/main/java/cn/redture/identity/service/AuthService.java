package cn.redture.identity.service;

import cn.redture.identity.pojo.dto.LoginRequestDTO;
import cn.redture.identity.pojo.dto.RegisterRequestDTO;
import cn.redture.identity.pojo.dto.TokenResponseDTO;

public interface AuthService {

    /**
     * 注册新用户并返回 JWT token。
     */
    TokenResponseDTO register(RegisterRequestDTO request);

    /**
     * 登录并返回 JWT token。
     */
    TokenResponseDTO login(LoginRequestDTO request);

    /**
     * 登出当前用户。
     * @param authorizationHeader 认证请求头
     */
    void logout(String authorizationHeader);

    /**
     * 使用刷新令牌换取新的访问令牌（以及新的刷新令牌）
     */
    TokenResponseDTO refreshToken(String expiredAccessToken, String refreshToken);
}
