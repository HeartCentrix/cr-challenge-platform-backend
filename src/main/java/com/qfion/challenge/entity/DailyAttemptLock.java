package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "daily_attempt_lock")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyAttemptLock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String identityType;
    private String identityHash;
    private LocalDate attemptDate;
    private Long candidateId;
    private Long attemptId;
    private OffsetDateTime createdAt;
}
