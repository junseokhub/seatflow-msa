package com.seatflow.seat.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class SseRedisConfig {

    public static final String SEAT_UPDATE_CHANNEL = "seat:updates";

    @Bean
    public RedisMessageListenerContainer seatUpdateListenerContainer(
            RedisConnectionFactory connectionFactory,
            SeatUpdateSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(SEAT_UPDATE_CHANNEL));
        return container;
    }
}