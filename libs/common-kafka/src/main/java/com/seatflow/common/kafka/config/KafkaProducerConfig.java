package com.seatflow.common.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaBootstrapProperties.class)
public class KafkaProducerConfig {

    // 도메인 객체를 JSON으로 직렬화해 발행하는 범용 템플릿 (기본값)
    @Bean
    public ProducerFactory<String, Object> producerFactory(
            KafkaBootstrapProperties kafkaProperties,
            ObjectMapper kafkaObjectMapper) {
        JsonSerializer<Object> jsonSerializer = new JsonSerializer<>(kafkaObjectMapper);

        Map<String, Object> config = baseConfig(kafkaProperties);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), jsonSerializer);
    }

    @Primary
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // 이미 직렬화된 문자열을 그대로 발행하는 템플릿 (이중 직렬화 방지)
    @Bean
    public ProducerFactory<String, String> stringProducerFactory(KafkaBootstrapProperties kafkaProperties) {
        Map<String, Object> config = baseConfig(kafkaProperties);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer());
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate(
            ProducerFactory<String, String> stringProducerFactory) {
        return new KafkaTemplate<>(stringProducerFactory);
    }

    private Map<String, Object> baseConfig(KafkaBootstrapProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return config;
    }
}