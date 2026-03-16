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
     * 全局兜底规则。
     */
    private Rule defaultRule = Rule.globalDefault();

    /**
     * 路径细分规则，匹配后和全局规则共同生效。
     */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        private String name;
        private String pattern;
        private int maxRequests;
        private int windowSeconds;
        private LimitKeyStrategy strategy = LimitKeyStrategy.IP;

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
}
