package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "attempt")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long candidateId;
    private Long questionId;
    private OffsetDateTime submittedAt;
    private Long durationMs;
    private Integer judgeLanguageId;
    @Column(columnDefinition = "text")
    private String sourceCode;
    private Integer testcasesPassed;
    private Integer testcasesTotal;
    private BigDecimal score;
    private BigDecimal speedBonus;
    private String judgeStatus;
    private String ipAddress;
    @Column(columnDefinition = "text")
    private String userAgent;
}
