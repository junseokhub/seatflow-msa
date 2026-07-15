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
payment-service      결제, 환불, 쿠폰 검증/확정
coupon-service       쿠폰 캠페인, 선착순 발급
```

서비스 간 통신은 Kafka 기반 이벤트로 이루어지며, 결제 금액 검증이나 쿠폰 유효성 확인처럼 그 순간의 정확한 값이 필요한 지점은 OpenFeign 동기 호출을 씁니다. 외부 진입점은 Kong Gateway(Gateway API)이며, 인증은 Gateway와 서비스 양쪽에서 이중으로 검증합니다.

## 기술 스택

기술을 고를 때 하나만 보고 정하지 않고, 대안을 나열해 트레이드오프를 비교한 뒤 선택했습니다.

| 영역 | 채택 | 비교한 대안 |
|---|---|---|
| 서비스 간 정합성 | Transactional Outbox | dual-write, `@TransactionalEventListener`, CDC(Debezium) |
| Outbox 구현 | JPA 공통 모듈(common-outbox-jpa) | JPA/Mongo 포트-어댑터 추상화(초기) ->실사용 분포(JPA 4:Mongo 1) 기준 재조정 |
| 좌석 동시성 제어 | Redis + Lua 원자 연산 | DB 락, 분산 락(Redisson) |
| 쿠폰 재고 동시성 제어 | 원자적 UPDATE(조건부 증가) + DB unique 제약 | 비관적 락, 애플리케이션 레벨 카운팅 |
| 다중 서비스 트랜잭션(취소·환불) | Saga(Orchestration) | Choreography, 2PC |
| 컨슈머 실패 처리 | DLQ(DefaultErrorHandler + DeadLetterPublishingRecoverer) | 무한 재시도, 로그 후 폐기 |
| API Gateway | Kong(Gateway API, Kong Operator) | nginx Ingress, Spring Cloud Gateway |
| Gateway 인증 확장 | 커스텀 Kong Lua 플러그인(RS256 검증 + 헤더 변환) | 표준 jwt 플러그인, pre-function(샌드박스 제약으로 폐기) |
| 서비스 인증 검증 위치 | 서비스별 자체 검증(패턴2) + Gateway 이중 검증 | Gateway 단일 검증(패턴1) |
| JWT 발급/검증 분리 | common-jwt(검증 전용 공통 모듈), auth-service(발급 전용) | 서비스별 개별 구현 |
| 서비스 간 동기 호출 장애 격리 | Resilience4j 서킷 브레이커 + 인증 헤더 전파(common-clients) | 재시도만, 타임아웃 없는 직접 호출 |
| 시크릿 관리 | 비교 예정 | Vault, Kubernetes Secret |
| CDC 실험 | Debezium(Outbox Event Router) | 비즈니스 테이블 직접 감시 vs Outbox 테이블 감시 |
| 인프라 | Kubernetes, GitOps(Jenkins ->ArgoCD, buildkit) | Strimzi Kaniko 빌드(아키텍처·권한 문제로 대체) |
| Language/Framework | Java 21, Spring Boot 3.5 | |
| Messaging | Kafka(Strimzi, KRaft) | |
| Storage | MySQL(서비스별 DB), MongoDB(show), Redis Cluster | |

## 인증/인가

- **발급과 검증의 분리**: private key는 auth-service만 갖고, public key와 검증 로직(JwtValidator)은 common-jwt 모듈로 공통화해 모든 서비스가 공유합니다.
- **이중 방어**: Kong의 커스텀 플러그인이 1차로 서명을 검증해 명백히 잘못된 요청을 걸러내고, 각 서비스도 자체적으로 다시 검증합니다. Gateway가 뚫리더라도 서비스 단에서 막히는 구조입니다.
- **인가**: 관리자 전용 API는 `@PreAuthorize`로, 개인 리소스는 요청 주체와 소유자 일치 여부를 확인해 타인의 예매·결제·쿠폰에 접근할 수 없도록 했습니다.

## 쿠폰 시스템

- **선착순 발급**: 관리자가 캠페인(수량, 할인액)을 등록하면 사용자가 경쟁적으로 발급받습니다. 재고 차감은 조건부 원자적 UPDATE로, 1인 1매는 DB unique 제약으로 강제해 동시 요청에도 재고 초과 발급이 없습니다.
- **할인 적용 시점**: 결제 시점에, PG를 호출하기 전 서버가 최종 금액을 확정합니다. PG는 할인 개념을 모르고 최종 금액만 처리합니다.
- **검증과 확정의 분리**: 쿠폰 유효성 검증(상태 변경 없음)과 실제 확정(결제 성공 후에만 실행)을 분리해, PG 실패 시 쿠폰만 소진되는 상황을 막았습니다.
- **서비스 경계**: 쿠폰 도메인 지식은 payment-service 안에만 존재합니다. 취소 시 환불과 쿠폰 복원을 같은 서비스, 같은 흐름에서 처리해 reservation-service가 쿠폰의 존재 자체를 몰라도 되게 설계했습니다.

## 테스트 전략

- **통합 테스트**: TestContainers로 실제 MySQL/Kafka/Redis에 대한 통합 검증 (예정)
- **Saga 시나리오 테스트**: 정상 완료, 취소 마감 초과, 환불 실패 시 보상(좌석 재점유·예매 원복)  -> 실제 이벤트를 주입해 직접 검증
- **장애 주입 테스트**: 결제 실패를 강제하는 mock 전략(TOSS)을 별도로 두어 보상 경로를 재현 가능하게 함
- **DLQ 테스트**: 손상된 메시지를 직접 발행해 재시도 -> 격리 -> 이후 메시지 정상 처리를 확인
- **부하 테스트**: k6로 오픈 트래픽 폭주 상황 재현 (예정)

## 블로그

전체 시리즈: [junseokoo.tistory.com](https://junseokoo.tistory.com)

각 편은 이전 편에서 발견한 문제를 다음 편에서 푸는 방식으로 이어지며, 선택한 방법뿐 아니라 검토한 대안과 그 이유, 실제 겪은 시행착오를 함께 기록하고있습니다.