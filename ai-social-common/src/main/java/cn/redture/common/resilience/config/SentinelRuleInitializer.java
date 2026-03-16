package cn.redture.common.resilience.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 启动时加载 Sentinel 规则，规则来源于 YAML 配置。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SentinelGovernanceProperties.class)
public class SentinelRuleInitializer {

    @Bean
    public ApplicationRunner sentinelRulesRunner(SentinelGovernanceProperties properties) {
        return args -> {
            if (!properties.isEnabled()) {
                log.info("Sentinel governance is disabled by config.");
                return;
            }

            List<FlowRule> flowRules = properties.getFlowRules().stream().map(item -> {
                FlowRule rule = new FlowRule();
                rule.setResource(item.getResource());
                rule.setCount(item.getCount());
                rule.setGrade(item.getGrade());
                rule.setControlBehavior(item.getControlBehavior());
                return rule;
            }).toList();

            List<DegradeRule> degradeRules = properties.getDegradeRules().stream().map(item -> {
                DegradeRule rule = new DegradeRule();
                rule.setResource(item.getResource());
                rule.setGrade(item.getGrade());
                rule.setCount(item.getCount());
                rule.setTimeWindow(item.getTimeWindow());
                rule.setMinRequestAmount(item.getMinRequestAmount());
                rule.setStatIntervalMs(item.getStatIntervalMs());
                return rule;
            }).toList();

            FlowRuleManager.loadRules(flowRules);
            DegradeRuleManager.loadRules(degradeRules);
            log.info("Sentinel rules loaded, flowRules={}, degradeRules={}", flowRules.size(), degradeRules.size());
        };
    }
}
