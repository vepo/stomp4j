package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import dev.vepo.stomp4j.client.StompClient;

@AutoConfiguration
@ConditionalOnClass(StompClient.class)
@ConditionalOnProperty(prefix = "stomp4j.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StompClientProperties.class)
public class StompClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StompClientConnectionManager stompClientConnectionManager(StompClientFactory factory) {
        return new StompClientConnectionManager(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClientFactory stompClientFactory(StompClientProperties properties,
                                                 ObjectProvider<javax.net.ssl.SSLContext> sslContext) {
        return new StompClientFactory(properties, sslContext.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClientTemplate stompClientTemplate(StompClientConnectionManager connectionManager,
                                                   StompClientProperties properties) {
        return new StompClientTemplate(connectionManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StompListenerEndpointRegistrar stompListenerEndpointRegistrar(
                                                                         StompClientConnectionManager connectionManager,
                                                                         StompListenerMethodInvoker invoker) {
        return new StompListenerEndpointRegistrar(connectionManager, invoker);
    }

    @Bean
    @ConditionalOnMissingBean
    public StompListenerMethodInvoker stompListenerMethodInvoker(
                                                                 ObjectProvider<TaskExecutor> taskExecutors) {
        var executor = taskExecutors.stream()
                                    .filter(bean -> !(bean instanceof org.springframework.core.task.SimpleAsyncTaskExecutor))
                                    .findFirst()
                                    .orElseGet(() -> taskExecutors.getIfAvailable(() -> new SimpleAsyncTaskExecutor("stomp-listener-")));
        return new StompListenerMethodInvoker(Objects.requireNonNull(executor));
    }

    @Bean(name = "stompListenerTaskExecutor")
    @ConditionalOnMissingBean(name = "stompListenerTaskExecutor")
    public TaskExecutor stompListenerTaskExecutor() {
        return new SimpleAsyncTaskExecutor("stomp-listener-");
    }
}
