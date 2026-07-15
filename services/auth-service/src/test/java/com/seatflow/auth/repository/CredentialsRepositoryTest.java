package com.seatflow.auth.repository;

import com.seatflow.auth.domain.Credentials;
import com.seatflow.common.security.Role;
import com.seatflow.common.test.composition.MysqlContainerSupport;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CredentialsRepositoryTest implements MysqlContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private CredentialsRepository credentialsRepository;

    private Credentials credentials(String userId, String email) {
        return Credentials.builder()
                .userId(userId).email(email).passwordHash("hashed-password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("existsByEmail()은 이메일 존재 여부를 정확히 반환한다")
    void existsByEmailReflectsActualPresence() {
        credentialsRepository.save(credentials("user-1", "test@example.com"));

        assertThat(credentialsRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(credentialsRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByEmail()은 정확히 일치하는 계정을 찾는다")
    void findByEmailFindsCorrectAccount() {
        credentialsRepository.save(credentials("user-1", "test@example.com"));

        Optional<Credentials> result = credentialsRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("findByUserId()는 정확히 일치하는 계정을 찾는다")
    void findByUserIdFindsCorrectAccount() {
        credentialsRepository.save(credentials("user-1", "test@example.com"));

        Optional<Credentials> result = credentialsRepository.findByUserId("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("같은 이메일로 중복 가입하면 unique 제약 위반이 난다")
    void duplicateEmailViolatesUniqueConstraint() {
        credentialsRepository.save(credentials("user-1", "test@example.com"));
        credentialsRepository.flush();

        Credentials duplicate = credentials("user-2", "test@example.com");   // 같은 이메일, 다른 userId

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    credentialsRepository.save(duplicate);
                    credentialsRepository.flush();
                });
    }

    @Test
    @DisplayName("같은 userId로 중복 저장하면 unique 제약 위반이 난다")
    void duplicateUserIdViolatesUniqueConstraint() {
        credentialsRepository.save(credentials("user-1", "test@example.com"));
        credentialsRepository.flush();

        Credentials duplicate = credentials("user-1", "another@example.com");   // 같은 userId, 다른 이메일

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    credentialsRepository.save(duplicate);
                    credentialsRepository.flush();
                });
    }

    @Test
    @DisplayName("role을 지정하지 않고 빌더로 생성하면(null) 기본값 USER로 저장된다")
    void defaultsToUserRoleWhenNotSpecified() {
        Credentials credentials = Credentials.builder()
                .userId("user-1").email("test@example.com").passwordHash("hash")
                .role(null)
                .build();

        Credentials saved = credentialsRepository.save(credentials);

        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }
}