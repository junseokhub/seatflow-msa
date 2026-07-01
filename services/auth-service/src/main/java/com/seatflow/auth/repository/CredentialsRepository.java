package com.seatflow.auth.repository;

import com.seatflow.auth.domain.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialsRepository extends JpaRepository<Credentials, String> {
    boolean existsByEmail(String email);
    Optional<Credentials> findByEmail(String email);
    Optional<Credentials> findByUserId(String userId);
}