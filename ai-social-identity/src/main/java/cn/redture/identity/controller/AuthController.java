package cn.redture.identity.controller;

import cn.redture.common.pojo.model.RestResult;
import cn.redture.identity.pojo.dto.LoginRequestDTO;
import cn.redture.identity.pojo.dto.RefreshTokenRequestDTO;
import cn.redture.identity.pojo.dto.RegisterRequestDTO;
import cn.redture.identity.pojo.dto.TokenResponseDTO;
import cn.redture.identity.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;


    @PostMapping("/register")
    public RestResult<TokenResponseDTO> register(@RequestBody RegisterRequestDTO request) {
        TokenResponseDTO response = authService.register(request);
        return RestResult.created(response);
    }

    @PostMapping("/login")
    public RestResult<TokenResponseDTO> login(@RequestBody LoginRequestDTO request) {
        TokenResponseDTO response = authService.login(request);
        return RestResult.success(response);
    }

    @PostMapping("/logout")
    public RestResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return RestResult.noContent();
    }

    @PostMapping("/refresh")
    public RestResult<TokenResponseDTO> refresh(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                @RequestBody RefreshTokenRequestDTO request) {
        String accessToken = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            accessToken = authorizationHeader.substring("Bearer ".length());
        }
        TokenResponseDTO response = authService.refreshToken(accessToken, request.getRefreshToken());
        return RestResult.success(response);
    }
}
