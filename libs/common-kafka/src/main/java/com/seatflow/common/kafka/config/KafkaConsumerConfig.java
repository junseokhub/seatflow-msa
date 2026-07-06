package com.seatflow.common.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaBootstrapProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            KafkaBootstrapProperties kafkaProperties) {

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
    }

    /**
     * 컨슈머 처리 실패 시 재시도 후 DLQ(원래 토픽 + ".DLT")로 보내는 공통 에러 핸들러.
     * 모든 서비스의 모든 @KafkaListener에 공통 적용된다(kafkaListenerContainerFactory가
     * 컨테이너 팩토리라 전체 리스너가 이걸 공유한다).
     *
     * - 역직렬화 실패(poison message)든, 처리 중 일시적 장애든 구분 없이 N번 재시도 후
     *   DLQ로 보낸다. 재시도로 해결되는 일시적 장애는 재시도 안에서 복구되고,
     *   메시지 자체가 잘못된 경우(poison)는 재시도해도 계속 실패하다 DLQ로 격리된다.
     * - DLQ로 보낸 뒤에는 원래 토픽의 offset을 커밋한다. 그래야 이 메시지 하나 때문에
     *   컨슈머 전체가 멈추지 않고 다음 메시지로 진행한다.
     * - 원본 메시지, 원래 토픽, 예외 정보가 DLQ 메시지 헤더에 그대로 실려서 나중에
     *   무엇이 왜 실패했는지 추적할 수 있다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);

        // 1초 간격으로 3번 재시도(최초 시도 포함 총 4번). 그래도 실패하면 DLQ로.
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}