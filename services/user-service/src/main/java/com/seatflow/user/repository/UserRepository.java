package com.seatflow.user.repository;

import com.seatflow.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);


    /**
     * INSERT IGNORE 멱등 생성.
     * 현재 활성 방식은 REQUIRES_NEW + save라 직접 쓰이지 않지만,
     * INSERT IGNORE 방식으로 되돌릴 때를 위해 남겨둔다.
     * id(PK)/email(unique) 충돌 시 예외 없이 0 반환. @PrePersist 우회하므로 값 직접 설정.
     */
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO users (id, email, name, status, created_at, updated_at)
        VALUES (:id, :email, :name, 'ACTIVE', :now, :now)
        """, nativeQuery = true)
    int insertIgnore(@Param("id") String id,
                     @Param("email") String email,
                     @Param("name") String name,
                     @Param("now") LocalDateTime now);
}