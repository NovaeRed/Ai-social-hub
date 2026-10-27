package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;

import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

class SseJmxTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMBeanExport(server = "sseMBeanServer")
    static class Config {
        @Bean
        MBeanServer sseMBeanServer() {
            return MBeanServerFactory.newMBeanServer();
        }

        @Bean
        SseEmitterServiceImpl service() {
            return new SseEmitterServiceImpl(new PushConfig());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosticsAreQueryableThroughManagedOperation() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(SseEmitterServiceImpl.class);
            service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "offline"));
            var server = context.getBean(MBeanServer.class);
            var result =
                    (Map<String, Object>)
                            server.invoke(
                                    new ObjectName("cn.redture.sse:type=Connections"),
                                    "diagnostics",
                                    new Object[0],
                                    new String[0]);
            assertEquals(0, result.get("registeredConnections"));
            assertEquals(
                    1,
                    ((Map<String, Long>) result.get("counters"))
                            .get("notification.noActiveConnection"));
        }
    }
}
