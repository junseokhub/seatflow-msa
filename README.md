# Seatflow

MSA로 구현한 티켓팅 시스템입니다. 실무 규모의 분산 시스템에서 마주치는 문제들(정합성, 멱등성, 장애 복구, 트래픽 폭주)을 직접 겪고, 대안을 비교해 선택하고, 실제로 검증하는 것을 목표로 만들었습니다.

## 아키텍처
build-logic 기반 모노레포 구성.
```
/libs/               공통모듈
auth-service         인증(JWT 발급/검증), 회원 크리덴셜, 역할(Role)
user-service         회원 프로필
show-service         공연 정보 (MongoDB)
seat-service         좌석 점유·확정 (Redis + MySQL)
reservation-service  예매, 취소 Saga 오케스트레이터
payment-service      결제, 환불
```

서비스 간 통신은 Kafka 기반 이벤트로 이루어지며, 결제 금액 검증처럼 그 순간의 정확한 값이 필요한 지점은 OpenFeign 동기 호출을 씁니다.

## 기술 스택

기술을 고를 때 하나만 보고 정하지 않고, 대안을 나열해 트레이드오프를 비교한 뒤 선택했습니다.

| 영역 | 채택 | 비교한 대안 |
|---|---|---|
| 서비스 간 정합성 | Transactional Outbox | dual-write, `@TransactionalEventListener`, CDC(Debezium) |
| Outbox 구현 | JPA 공통 모듈(common-outbox-jpa) | JPA/Mongo 포트-어댑터 추상화(초기) → 실사용 분포(JPA 4:Mongo 1) 기준 재조정 |
| 좌석 동시성 제어 | Redis + Lua 원자 연산 | DB 락, 분산 락(Redisson) |
| 다중 서비스 트랜잭션(취소·환불) | Saga(Orchestration) | Choreography, 2PC |
| 컨슈머 실패 처리 | DLQ(DefaultErrorHandler + DeadLetterPublishingRecoverer) | 무한 재시도, 로그 후 폐기 |
| API Gateway | 비교 진행 중 | nginx Ingress, Kong, Spring Cloud Gateway |
| 시크릿 관리 | 비교 예정 | Vault, Kubernetes Secret |
| CDC 실험 | Debezium(Outbox Event Router) | 비즈니스 테이블 직접 감시 vs Outbox 테이블 감시 |
| 인프라 | Kubernetes, GitOps(Jenkins → ArgoCD, buildkit) | Strimzi Kaniko 빌드(아키텍처·권한 문제로 대체) |
| Language/Framework | Java 21, Spring Boot 3.5 | |
| Messaging | Kafka(Strimzi, KRaft) | |
| Storage | MySQL(서비스별 DB), MongoDB(show), Redis Cluster | |

## 테스트 전략

- **통합 테스트**: TestContainers로 실제 MySQL/Kafka/Redis에 대한 통합 검증 (예정)
- **Saga 시나리오 테스트**: 정상 완료, 취소 마감 초과, 환불 실패 시 보상(좌석 재점유·예매 원복)  -> 실제 이벤트를 주입해 직접 검증
- **장애 주입 테스트**: 결제 실패를 강제하는 mock 전략(TOSS)을 별도로 두어 보상 경로를 재현 가능하게 함
- **DLQ 테스트**: 손상된 메시지를 직접 발행해 재시도 -> 격리 -> 이후 메시지 정상 처리를 확인
- **부하 테스트**: k6로 오픈 트래픽 폭주 상황 재현 (예정)

## 블로그

전체 시리즈: [junseokoo.tistory.com](https://junseokoo.tistory.com)

각 편은 이전 편에서 발견한 문제를 다음 편에서 푸는 방식으로 이어지며, 선택한 방법뿐 아니라 검토한 대안과 그 이유, 실제 겪은 시행착오를 함께 기록하고있습니다.