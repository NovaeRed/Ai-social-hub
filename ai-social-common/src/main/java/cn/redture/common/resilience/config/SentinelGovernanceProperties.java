package cn.redture.common.resilience.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则配置，支持通过 YAML 扩展资源和阈值。
 */
@Data
@ConfigurationProperties(prefix = "app.resilience.sentinel")
public class SentinelGovernanceProperties {

    private boolean enabled = true;

    private List<FlowRuleItem> flowRules = new ArrayList<>();

    private List<DegradeRuleItem> degradeRules = new ArrayList<>();

    @Data
    public static class FlowRuleItem {
        private String resource;
        /**
         * 流控阈值，QPS 或并发线程数。
         */
        private double count;
        /**
         * 1:QPS, 0:并发线程数。
         */
        private int grade = 1;
        /**
         * 0:快速失败。
         */
        private int controlBehavior = 0;
    }

    @Data
    public static class DegradeRuleItem {
        private String resource;
        /**
         * 0:慢调用比例, 1:异常比例, 2:异常数。
         */
        private int grade = 1;
        /**
         * 触发阈值；异常比例场景建议 0~1。
         */
        private double count;
        /**
         * 熔断时长（秒）。
         */
        private int timeWindow = 10;
        /**
         * 最小请求数。
         */
        private int minRequestAmount = 20;
        /**
         * 统计窗口毫秒。
         */
        private int statIntervalMs = 60000;
    }
}
