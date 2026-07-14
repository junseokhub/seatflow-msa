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
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

/**
 * 운영은 Redis Cluster(3노드)를 쓰지만, 통합테스트(TestContainers)는 단일 Redis 컨테이너만 띄운다.
 * 클러스터를 테스트에서까지 재현하는 건 검증하려는 대상 (hold/TTL 로직)에 비해 인프라 구성 비용이 너무 크다.
 * 이 커넥션 팩토리가 "클러스터냐 단일이냐"를 결정할 뿐,
 * hold/TTL 같은 애플리케이션 로직은 두 모드 어느 쪽에서도 동일하게 동작해야 정상이므로 로직 자체의 신뢰도에는 차이가 없다.
 * test 프로필이 활성화된 경우에만 standalone 빈이 뜨고, 그 외(운영 포함 전부)는 기존 클러스터 빈이 그대로 뜬다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    private final RedisProperties redisProperties;

    // 테스트 아닐경우 클러스터모
    @Bean
    @Profile("!test")
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

    /**
     * 테스트 전용. spring.data.redis.host/port(TestContainers가 @DynamicPropertySource로
     * 주입)를 그대로 읽어 단일 노드로 연결한다. 비밀번호는 테스트 컨테이너에 안 걸었으므로 비워둔다
     * (필요하면 REDIS 컨테이너에 --requirepass로 맞출 수도 있으나, 지금은 테스트 목적상 불필요).
     */
    @Bean
    @Profile("test")
    public RedisConnectionFactory testRedisConnectionFactory(
            org.springframework.core.env.Environment env) {
        String host = env.getProperty("spring.data.redis.host");
        int port = Integer.parseInt(env.getProperty("spring.data.redis.port"));
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
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