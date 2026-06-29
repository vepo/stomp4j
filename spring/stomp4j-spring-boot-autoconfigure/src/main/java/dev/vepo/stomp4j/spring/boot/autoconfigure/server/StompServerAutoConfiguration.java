package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import dev.vepo.stomp4j.server.StompConnectionListener;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.SubscriptionHandler;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;

@AutoConfiguration
@ConditionalOnClass(StompServer.class)
@ConditionalOnProperty(prefix = "stomp4j.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StompServerProperties.class)
public class StompServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StompOutboundTemplate stompOutboundTemplate(StompServer server) {
        return new StompOutboundTemplate(server);
    }

    @Bean
    @ConditionalOnMissingBean
    public StompServer stompServer(StompServerProperties properties,
                                   ObjectProvider<StompInboundHandler> inboundHandlers,
                                   ObjectProvider<StompAuthenticator> authenticator,
                                   ObjectProvider<SubscriptionHandler> subscriptionHandler,
                                   ObjectProvider<StompConnectionListener> connectionListener) {
        var builder = StompServer.builder()
                                 .serverName(properties.getServerName())
                                 .heartbeat(properties.getHeartbeat());
        properties.getChannels().forEach(channel -> builder.channel(channel.getType(), channel.getPort()));
        if (properties.getChannels().isEmpty()) {
            builder.channel(dev.vepo.stomp4j.commons.TransportType.TCP, 5500);
        }
        var handlers = inboundHandlers.orderedStream().toList();
        if (handlers.isEmpty()) {
            builder.handler(message -> {});
        } else {
            builder.handler(new CompositeStompInboundHandler(handlers));
        }
        subscriptionHandler.ifAvailable(builder::subscription);
        if (subscriptionHandler.getIfAvailable() == null) {
            builder.subscription(topic -> true);
        }
        authenticator.ifAvailable(builder::authenticator);
        connectionListener.ifAvailable(builder::connectionListener);
        return builder.start();
    }

    @Bean
    @ConditionalOnMissingBean
    public StompServerLifecycle stompServerLifecycle(StompServer server) {
        return new StompServerLifecycle(server);
    }
}
