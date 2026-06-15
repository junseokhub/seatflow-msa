package com.seatflow.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Credentials {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    private Credentials(String userId, String email, String passwordHash) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}