package com.seatflow.seat.batch;

import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Airflow KubernetesPodOperator로 띄우는 테스트 배치.
 * 같은 이미지에 --batch.job=hello 를 주면 이 러너만 돌고 프로세스가 종료된다(인자 없으면 빈 자체가 없음).
 * 서비스 파드의 컨슈머 그룹을 뺏지 않도록 파드 쪽에서 --spring.kafka.listener.auto-startup=false 를 함께 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.job", havingValue = "hello")
public class HelloBatchRunner implements ApplicationRunner {

    private final SeatRepository seatRepository;
    private final ConfigurableApplicationContext context;

    @Value("${batch.date:}")
    private String batchDate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("hello batch start date={} seats={}", batchDate, seatRepository.count());
        // 웹 서버가 떠 있어서 러너가 끝나도 JVM이 안 내려가므로 직접 종료. exit code가 곧 Airflow 태스크 성공/실패.
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
