package com.seatflow.user.idempotency;

import com.seatflow.user.domain.User;
import com.seatflow.user.inbox.IdempotentEventProcessor;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [Inbox 패턴] — 비교 학습용, 실제로 운영 컨슈머에서는 쓰지 않는다.
 *
 * 별도의 processed_event 테이블에 (consumerGroup, eventId) 조합을 먼저 기록하고,
 * 그 기록이 성공했을 때만 비즈니스 로직(유저 생성)을 실행한다. User는 PK(id)와
 * email에 이미 자연스러운 unique 제약이 있어서, 이 방식이 주는 이점(별도 테이블로
 * "이벤트 처리 여부"를 추적)이 크지 않다 — 오히려 테이블이 하나 더 늘고 관리
 * 포인트가 생긴다는 비용만 있다. 그래서 최종적으로 [4](noRollbackFor)를 채택했다.
 */
@Service("userServiceInboxPattern")
@RequiredArgsConstructor
public class UserServiceInboxPattern {

    private static final String CONSUMER_GROUP = "user-service";

    private final IdempotentEventProcessor idempotentEventProcessor;
    private final UserRepository userRepository;

    public void createUser(String eventId, String userId, String email, String name) {
        idempotentEventProcessor.process(CONSUMER_GROUP, eventId, "user.registered", () -> {
            User user = User.builder().id(userId).email(email).name(name).build();
            userRepository.save(user);
        });
    }
}