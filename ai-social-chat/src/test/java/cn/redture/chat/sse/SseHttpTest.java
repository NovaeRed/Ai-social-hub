package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

class SseHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Mvc {
        @Bean
        Controller controller(SseEmitterServiceImpl service) {
            return new Controller(service);
        }
    }

    @RestController
    static class Controller {
        final SseEmitterServiceImpl service;

        Controller(SseEmitterServiceImpl service) {
            this.service = service;
        }

        @GetMapping(value = "/sse", produces = "text/event-stream")
        SseEmitter subscribe() {
            return service.createEmitter(1L);
        }
    }

    @Test
    void actualHttpReplacementRemainsRegisteredAndReceivesBusinessEvent() throws Exception {
        try (var rig = new Rig();
                var first = rig.subscribe()) {
            EmitterContext old = rig.service.connections(1L).getFirst();
            try (var second = rig.subscribe()) {
                assertEquals(1, rig.service.connections(1L).size());
                assertNotSame(old, rig.service.connections(1L).getFirst());
                old.getWriterThread().join(2000);
                assertFalse(old.getWriterThread().isAlive());
                rig.service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "over-real-http"));
                assertTrue(readUntil(second, "over-real-http").contains("MESSAGE_CREATED"));
            }
        }
    }

    @Test
    void stoppedTcpReaderIsDetectedAndSocketWriterAndQueueReleaseBeforeClientCloses()
            throws Exception {
        try (var rig = new Rig();
                var client = rig.subscribe()) {
            var context = rig.service.connections(1L).getFirst();
            String payload = "x".repeat(512 * 1024);
            for (int i = 0; i < 20; i++)
                rig.service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", payload));
            // Client deliberately does not read any more bytes, and is not closed until assertions
            // finish.
            SseWriterTest.await(
                    () -> SseWriterTest.count(rig.service, "connection.closed.WRITE_TIMEOUT") == 1);
            context.getWriterThread().join(3000);
            assertFalse(
                    context.getWriterThread().isAlive(),
                    "writer must exit without client closing socket");
            SseWriterTest.await(
                    () ->
                            ((Number) rig.service.diagnostics().get("liveResources")).intValue()
                                    == 0);
            SseWriterTest.await(() -> rig.protocol().openSocketCount() == 0);
            assertTrue(rig.service.connections(1L).isEmpty());
            assertEquals(0, context.getQueueSize());
            assertEquals(0, context.getQueueBytes());
            assertTrue(SseWriterTest.count(rig.service, "event.abandoned") > 0);
            assertEquals(1, SseWriterTest.count(rig.service, "connection.closed.WRITE_TIMEOUT"));
        }
    }

    static String readUntil(Socket socket, String expected) throws Exception {
        var bytes = new ByteArrayOutputStream();
        while (!bytes.toString(StandardCharsets.UTF_8).contains(expected)) {
            int value = socket.getInputStream().read();
            assertTrue(value >= 0, "EOF before " + expected);
            bytes.write(value);
            assertTrue(bytes.size() < 32_768, "unexpected oversized response");
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    static final class Rig implements AutoCloseable {
        final GenericApplicationContext parent = new GenericApplicationContext();
        final SseEmitterServiceImpl service;
        final TomcatWebServer server;

        Rig() throws Exception {
            var config = new PushConfig();
            config.setMaxConnectionsPerUser(1);
            config.setHeartbeatIntervalMs(100);
            config.setInspectionIntervalMs(20);
            config.setWriteStallTimeoutMs(200);
            config.setTransportWriteTimeoutMs(700);
            config.setWarningSustainMs(0);
            config.setMaxQueueBytes(16 * 1024 * 1024);
            parent.registerBean(PushConfig.class, () -> config);
            parent.registerBean(
                    SseEmitterServiceImpl.class, () -> new SseEmitterServiceImpl(config));
            parent.refresh();
            service = parent.getBean(SseEmitterServiceImpl.class);
            var factory = new TomcatServletWebServerFactory(0);
            factory.setProtocol(InspectableProtocol.class.getName());
            factory.setAddress(InetAddress.getByName("127.0.0.1"));
            new SseTomcatConfiguration(config).customize(factory);
            server =
                    (TomcatWebServer)
                            factory.getWebServer(
                                    servletContext -> {
                                        var context = new AnnotationConfigWebApplicationContext();
                                        context.setParent(parent);
                                        context.register(Mvc.class);
                                        var servlet =
                                                servletContext.addServlet(
                                                        "mvc", new DispatcherServlet(context));
                                        servlet.setLoadOnStartup(1);
                                        servlet.setAsyncSupported(true);
                                        servlet.addMapping("/");
                                    });
            server.start();
            assertEquals(700, protocol().getConnectionTimeout());
        }

        InspectableProtocol protocol() {
            return (InspectableProtocol) server.getTomcat().getConnector().getProtocolHandler();
        }

        Socket subscribe() throws Exception {
            var socket = new Socket();
            socket.setReceiveBufferSize(1024);
            socket.setSoTimeout(5000);
            socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 3000);
            socket.getOutputStream()
                    .write(
                            "GET /sse HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: text/event-stream\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            assertTrue(readUntil(socket, "SSE connection successful").contains("200"));
            return socket;
        }

        @Override
        public void close() {
            service.close();
            server.stop();
            parent.close();
        }
    }

    public static class InspectableProtocol extends org.apache.coyote.http11.Http11NioProtocol {
        public int openSocketCount() {
            return getEndpoint().getConnections().size();
        }
    }
}
