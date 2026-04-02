package cn.redture.chat.sse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat.sse.push")
public class PushConfig {
    private int maxConnectionsPerUser = 5;
    private int queueCapacity = 100;
    private long heartbeatIntervalMs = 30000;
    private int maxSlowCount = 3;
    private long connectionTimeoutMs = 1800000;
}
