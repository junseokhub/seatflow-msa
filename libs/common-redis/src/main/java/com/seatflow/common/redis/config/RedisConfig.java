package com.seatflow.common.redis.config;

import com.seatflow.common.redis.properties.RedisProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    private final RedisProperties redisProperties;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        List<RedisNode> redisNodes = redisProperties.cluster().nodes().stream()
                .map(node -> new RedisNode(node.split(":")[0], Integer.parseInt(node.split(":")[1])))
                .toList();

        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration();
        clusterConfiguration.setClusterNodes(redisNodes);
        clusterConfiguration.setPassword(redisProperties.password());

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(100L))
                .keepAlive(true)
                .build();

        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .dynamicRefreshSources(true)
                .enableAllAdaptiveRefreshTriggers()
                .enablePeriodicRefresh(Duration.ofMinutes(30L))
                .build();

        ClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .socketOptions(socketOptions)
                .build();

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofMillis(3000L))
                .build();

        return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}