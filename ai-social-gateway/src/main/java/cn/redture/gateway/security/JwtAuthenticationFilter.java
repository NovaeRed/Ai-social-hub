package cn.redture.gateway.security;

import cn.redture.common.dto.UserPrincipal;
import cn.redture.common.exception.BaseException;
import cn.redture.common.exception.jwt.InvalidTokenException;
import cn.redture.common.exception.jwt.TokenBlacklistedException;
import cn.redture.common.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static cn.redture.gateway.security.AuthConstants.TOKEN_PREFIX;
import static cn.redture.common.constants.RedisConstants.AUTH_JWT_BLACKLIST_KEY_PREFIX;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String token = resolveToken(request);

        // 对于 auth 相关路径，直接放行
        if (path.startsWith("/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        if (token != null) {
            try {
                validateToken(token); // 验证 token，失败会抛出异常

                Map<String, Object> claims = jwtUtil.getClaimsFromToken(token);
                Object usernameObj = claims.get("username");
                Object uidObj = claims.get("uid");

                String username = usernameObj != null ? usernameObj.toString() : null;
                String uid = uidObj != null ? uidObj.toString() : null;

                if (username != null && uid != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserPrincipal principal = new UserPrincipal(uid, username, token);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (BaseException e) {
                // 捕获我们自定义的认证异常，并委托给全局异常处理器处理
                handlerExceptionResolver.resolveException(request, response, null, e);
                return; // 终止过滤器链
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 验证令牌，如果无效则抛出相应的异常
     * @param token 令牌
     */
    private void validateToken(String token) {
        // 1. 基础验证（签名、过期时间），失败会抛出 ExpiredTokenException 或 InvalidTokenException
        jwtUtil.validateToken(token);

        // 2. 检查黑名单
        try {
            String jti = jwtUtil.getJtiFromToken(token);
            String key = AUTH_JWT_BLACKLIST_KEY_PREFIX + jti;
            if (stringRedisTemplate.hasKey(key)) {
                throw new TokenBlacklistedException();
            }
        } catch (BaseException e) {
            // 如果是 TokenBlacklistedException，直接重新抛出
            if (e instanceof TokenBlacklistedException) {
                throw e;
            }
            // 其他解析 JTI 时的 BaseException，统一视为无效 Token
            throw new InvalidTokenException("无法解析令牌标识");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AuthConstants.TOKEN_HEADER);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
