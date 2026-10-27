package cn.redture.chat.sse;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/** Opt-in Tomcat HTTP/1.1 NIO blocking write timeout. Applies to the whole connector. */
@Configuration(proxyBeanMethods = false)
public class SseTomcatConfiguration
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final PushConfig config;

    public SseTomcatConfiguration(PushConfig config) {
        this.config = config;
    }

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
