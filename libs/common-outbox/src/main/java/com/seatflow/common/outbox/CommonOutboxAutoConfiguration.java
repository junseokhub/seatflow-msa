package com.seatflow.common.outbox;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

@AutoConfiguration(before = JpaRepositoriesAutoConfiguration.class)
@Import({
        CommonOutboxAutoConfiguration.OutboxPackageRegistrar.class,
        OutboxStore.class,
        OutboxPublisher.class
})
public class CommonOutboxAutoConfiguration {
    /**
     * outbox 패키지를 부트 auto-config base package 목록에 추가
     * @EnableJpaRepositories/@EntityScan 를 직접 선언하지 않으므로,
     * 소비 서비스의 기본 리포지토리/엔티티 스캔을 대체하지 않고 그 위에 outbox 패키지만 얹는다.
     */
    static class OutboxPackageRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AutoConfigurationPackages.register(registry, OutboxRepository.class.getPackageName());
        }
    }
}