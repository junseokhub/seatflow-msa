package com.seatflow.common.outbox.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.kafka.config.CommonKafkaAutoConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

/**
 * 이 모듈(common-outbox-jpa) 하나만 의존성에 추가하면 서비스는 별도 설정 없이
 * JPA 기반 Outbox 스케줄러 + 적재 헬퍼가 자동으로 뜬다.
 *
 * @EntityScan을 이 패키지로 명시해서, 서비스의 @SpringBootApplication 엔티티 스캔
 * 범위가 좁아도 Outbox 엔티티가 항상 인식되게 한다.
 */
@AutoConfiguration(after = CommonKafkaAutoConfiguration.class)
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackages = "com.seatflow.common.outbox.jpa")
@Import(OutboxScheduler.class)
public class CommonOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxRepository.class)
    public OutboxRepository outboxRepository(EntityManager entityManager) {
        return new JpaRepositoryFactory(entityManager).getRepository(OutboxRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxStore.class)
    public OutboxStore jpaOutboxStore(OutboxRepository outboxRepository) {
        return new OutboxStore(outboxRepository);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxAppender.class)
    public OutboxAppender outboxAppender(OutboxRepository outboxRepository,
                                         ObjectMapper kafkaObjectMapper) {
        return new OutboxAppender(outboxRepository, kafkaObjectMapper);
    }
}