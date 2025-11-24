package cn.redture.identity.controller;

import cn.redture.common.model.RestResult;
import cn.redture.identity.dto.LoginRequest;
import cn.redture.identity.dto.RefreshTokenRequest;
import cn.redture.identity.dto.RegisterRequest;
import cn.redture.identity.dto.TokenResponse;
import cn.redture.identity.service.AuthService;
import jakarta.annotation.Resource;
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
    public RestResult<Void> logout() {
        authService.logout();
        return RestResult.noContent();
    }

    @PostMapping("/refresh")
    public RestResult<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request.getRefreshToken());
        return RestResult.success(response);
    }
}
