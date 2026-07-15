package com.seatflow.user.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest implements MysqlContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private UserRepository userRepository;

    private User user(String id, String email) {
        return User.builder().id(id).email(email).name("테스트유저").build();
    }

    @Test
    @DisplayName("findByEmail()은 정확히 일치하는 유저를 찾는다")
    void findByEmailFindsCorrectUser() {
        userRepository.save(user("user-1", "test@example.com"));

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("existsByEmail()은 이메일 존재 여부를 정확히 반환한다")
    void existsByEmailReflectsActualPresence() {
        userRepository.save(user("user-1", "test@example.com"));

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    @DisplayName("같은 id(PK)로 save()를 두 번 하면 예외 없이 UPDATE(merge)로 처리된다 - INSERT 충돌이 아니다")
    void savingSameIdTwiceMergesInsteadOfThrowing() {
        /**
         * User.id는 @GeneratedValue가 아니라 애플리케이션이 직접 지정하는 값이다.
         * Hibernate는 save() 호출 시 이 id를 가진 엔티티가 이미 있으면 INSERT가 아니라 UPDATE(merge)로 처리한다.
         * 그래서 PK 중복은 save()로는 예외를 재현할 수 없다(실제로 겪었다).
         * unique 제약 위반을 확인하려면 email처럼 @GeneratedValue가 아닌 다른 unique 컬럼으로 검증해야 한다.
         * 아래 duplicateEmailViolatesUniqueConstraint 참고
         */
        userRepository.save(user("user-1", "test@example.com"));
        userRepository.flush();

        User duplicate = user("user-1", "another@example.com");
        userRepository.save(duplicate);
        userRepository.flush();

        User result = userRepository.findById("user-1").orElseThrow();
        assertThat(result.getEmail()).isEqualTo("another@example.com");   // 덮어써짐, 예외 없음
    }

    @Test
    @DisplayName("같은 email로 다른 id를 저장하면 unique 제약 위반이 난다")
    void duplicateEmailViolatesUniqueConstraint() {
        userRepository.save(user("user-1", "test@example.com"));
        userRepository.flush();

        User duplicate = user("user-2", "test@example.com");   // 다른 id, 같은 email

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    userRepository.save(duplicate);
                    userRepository.flush();
                });
    }

    @Test
    @DisplayName("insertIgnore()는 신규면 1(성공)을 반환하고 실제로 저장한다")
    void insertIgnoreInsertsNewRow() {
        int result = userRepository.insertIgnore("user-1", "test@example.com", "테스트유저", LocalDateTime.now());

        assertThat(result).isEqualTo(1);
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("insertIgnore()는 중복 id면 예외 없이 0을 반환한다 (INSERT IGNORE 특성)")
    void insertIgnoreSkipsSilentlyOnDuplicateId() {
        userRepository.insertIgnore("user-1", "test@example.com", "테스트유저", LocalDateTime.now());

        int result = userRepository.insertIgnore("user-1", "different@example.com", "다른이름", LocalDateTime.now());

        assertThat(result).isEqualTo(0);
        // 원래 저장된 email 그대로 유지되어야 한다(두 번째 시도는 완전히 무시됨).
        Optional<User> saved = userRepository.findByEmail("test@example.com");
        assertThat(saved).isPresent();
    }
}