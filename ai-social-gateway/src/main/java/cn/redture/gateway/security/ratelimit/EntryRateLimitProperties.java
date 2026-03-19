package cn.redture.gateway.security.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 入口限流配置，支持全局规则和路径细分规则。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.entry-rate-limit")
public class EntryRateLimitProperties {

    /**
     * 是否启用入口限流。
     */
    private boolean enabled = true;

    /**
     * 不参与限流的路径。
     */
    private List<String> excludePatterns = new ArrayList<>();

    /**
     * 受信任代理地址或网段（CIDR），用于从代理头回溯真实客户端 IP。
     */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * 全局兜底规则。
     */
    private Rule defaultRule = Rule.globalDefault();

    /**
     * 路径细分规则，匹配后和全局规则共同生效。
     */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        /**
         * 规则名称
         */
        private String name;

        /**
         * 路径匹配模式
         */
        private String pattern;

        /**
         * 在 windowSeconds 秒内，最多允许 maxRequests 次请求
         */
        private int maxRequests;

        /**
         * 限流窗口大小，单位秒。对于固定窗口算法，表示统计周期；对于令牌桶算法，表示令牌完全补满所需的时间
         */
        private int windowSeconds;

        /**
         * 限流算法
         */
        private RateLimitAlgorithm algorithm = RateLimitAlgorithm.TOKEN_BUCKET;

        /**
         * 桶容量
         */
        private Integer capacity;

        /**
         * 每次补充的令牌数量，默认为 maxRequests
         */
        private Integer refillTokens;

        /**
         * 补充令牌的周期，单位毫秒，默认为 windowSeconds * 1000
         */
        private Long refillPeriodMs;

        /**
         * 限流键的构建策略，默认为 IP 地址。USER_OR_IP 表示优先使用用户 ID，未登录时回退到 IP 地址
         */
        private LimitKeyStrategy strategy = LimitKeyStrategy.IP;

        public int effectiveCapacity() {
            return capacity != null && capacity > 0 ? capacity : Math.max(maxRequests, 1);
        }

        public int effectiveRefillTokens() {
            return refillTokens != null && refillTokens > 0 ? refillTokens : Math.max(maxRequests, 1);
        }

        public long effectiveRefillPeriodMs() {
            if (refillPeriodMs != null && refillPeriodMs > 0) {
                return refillPeriodMs;
            }
            return Math.max(windowSeconds, 1) * 1000L;
        }

        public static Rule globalDefault() {
            Rule rule = new Rule();
            rule.setName("global-default");
            rule.setPattern("/**");
            rule.setMaxRequests(240);
            rule.setWindowSeconds(60);
            rule.setStrategy(LimitKeyStrategy.IP);
            return rule;
        }
    }

    public enum LimitKeyStrategy {
        IP,
        USER_OR_IP
    }

    public enum RateLimitAlgorithm {
        FIXED_WINDOW,
        TOKEN_BUCKET
    }
}
