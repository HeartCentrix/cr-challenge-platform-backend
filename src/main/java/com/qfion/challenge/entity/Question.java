package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "question")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String slug;
    private String title;
    @Column(columnDefinition = "text")
    private String prompt;
    private Integer difficulty;
    private String language;
    private Integer judgeLanguageId;
    @Column(columnDefinition = "text")
    private String starterCode;
    @Column(columnDefinition = "text")
    private String referenceSolution;
    private Integer timeLimitSeconds;
    private BigDecimal maxScore;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
