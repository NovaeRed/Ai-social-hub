package cn.redture.identity.controller;

import cn.redture.common.model.RestResult;
import cn.redture.identity.pojo.dto.LoginRequest;
import cn.redture.identity.pojo.dto.RefreshTokenRequest;
import cn.redture.identity.pojo.dto.RegisterRequest;
import cn.redture.identity.pojo.dto.TokenResponse;
import cn.redture.identity.service.AuthService;
import jakarta.annotation.Resource;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;


    @PostMapping("/register")
    public RestResult<TokenResponse> register(@RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return RestResult.created(response);
    }

    @PostMapping("/login")
    public RestResult<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return RestResult.success(response);
    }

    @PostMapping("/logout")
    public RestResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return RestResult.noContent();
    }

    @PostMapping("/refresh")
    public RestResult<TokenResponse> refresh(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                             @RequestBody RefreshTokenRequest request) {
        String accessToken = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            accessToken = authorizationHeader.substring("Bearer ".length());
        }
        TokenResponse response = authService.refreshToken(accessToken, request.getRefreshToken());
        return RestResult.success(response);
    }
}
