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