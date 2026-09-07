package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attempt_result")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttemptResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long attemptId;
    private Long testcaseId;
    private Boolean isPassed;
    private String judgeStatus;
    private Integer execTimeMs;
    private Integer memoryKb;
    @Column(columnDefinition = "text")
    private String stdoutText;
}
