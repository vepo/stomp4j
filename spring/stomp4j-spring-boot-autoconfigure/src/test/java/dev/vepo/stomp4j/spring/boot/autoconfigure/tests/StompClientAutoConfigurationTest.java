package dev.vepo.stomp4j.spring.boot.autoconfigure.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.Acknowledgment;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientAutoConfiguration;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientTemplate;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompListener;
import dev.vepo.stomp4j.spring.boot.autoconfigure.tests.infra.StompContainer;

@SpringBootTest
@ContextConfiguration(classes = StompClientAutoConfigurationTest.TestApplication.class, initializers = StompClientAutoConfigurationTest.BrokerInitializer.class)
@ExtendWith(StompContainer.class)
class StompClientAutoConfigurationTest {

    static class BrokerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            StompContainer.ensureStarted();
            var broker = StompContainer.broker();
            TestPropertyValues.of("stomp4j.client.url=" + broker.tcpUrl(),
                                  "stomp4j.client.username=" + broker.username(),
                                  "stomp4j.client.password=" + broker.password())
                              .applyTo(context.getEnvironment());
        }
    }

    @SpringBootApplication
    @ImportAutoConfiguration(StompClientAutoConfiguration.class)
    static class TestApplication {

        @Bean
        TestConsumer testConsumer() {
            return new TestConsumer();
        }
    }

    static class TestConsumer {

        private final AtomicReference<String> lastMessage = new AtomicReference<>();

        String lastMessage() {
            return lastMessage.get();
        }

        @StompListener(destination = "/queue/spring-test", ackMode = AckMode.CLIENT_INDIVIDUAL)
        void onMessage(StompDelivery delivery, Acknowledgment acknowledgment) {
            lastMessage.set(delivery.body());
            acknowledgment.acknowledge();
        }
    }

    @Autowired
    private StompClientTemplate stompClientTemplate;

    @Autowired
    private TestConsumer testConsumer;

    @Test
    void shouldAutoConfigureClientAndDeliverToListener() throws Exception {
        var receipt = stompClientTemplate.sendWithReceipt("/queue/spring-test", "hello-spring");
        receipt.completion().get();
        await().atMost(Duration.ofSeconds(15)).until(() -> "hello-spring".equals(testConsumer.lastMessage()));
        assertThat(testConsumer.lastMessage()).isEqualTo("hello-spring");
    }
}
