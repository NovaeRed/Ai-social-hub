package cn.redture.gateway.security.ratelimit;

import cn.redture.common.pojo.model.RestResult;
import cn.redture.gateway.security.ratelimit.core.RateLimitConsumeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 入口层限流过滤器，在认证前执行，优先保护系统容量。
 */
@Component
@RequiredArgsConstructor
public class EntryRateLimitFilter extends OncePerRequestFilter {

    private final EntryRateLimiterService entryRateLimiterService;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        RateLimitConsumeResult result = entryRateLimiterService.check(request);
        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (result.retryAfterSeconds() > 0) {
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            }
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            if (result.ruleName() != null && !result.ruleName().isBlank()) {
                response.setHeader("X-RateLimit-Rule", result.ruleName());
            }
            response.getWriter().write(objectMapper.writeValueAsString(
                    RestResult.tooManyRequests("请求频繁，已触发入口限流，请稍后重试")
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
