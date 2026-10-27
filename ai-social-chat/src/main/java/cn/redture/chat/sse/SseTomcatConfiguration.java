package cn.redture.chat.sse;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/** 配置 Tomcat HTTP/1.1 NIO 的传输超时。 */
@Configuration(proxyBeanMethods = false)
public class SseTomcatConfiguration
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final PushConfig config;

    public SseTomcatConfiguration(PushConfig config) {
        this.config = config;
    }

    /**
     * 启用传输预算时，设置整个 Tomcat NIO 连接器的读写超时。
     *
     * @param factory 待定制的 Tomcat 服务工厂
     */
    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (config.getTransportWriteTimeoutMs() <= 0) return;
        factory.addConnectorCustomizers(
                connector -> {
                    if (!(connector.getProtocolHandler() instanceof Http11NioProtocol protocol)) {
                        throw new IllegalStateException(
                                "SSE transport timeout requires the verified Tomcat HTTP/1.1 NIO"
                                        + " connector");
                    }
                    protocol.setConnectionTimeout(
                            Math.toIntExact(config.getTransportWriteTimeoutMs()));
                });
    }
}
