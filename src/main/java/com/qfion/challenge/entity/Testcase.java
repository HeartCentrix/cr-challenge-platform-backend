package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "question_testcase")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Testcase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long questionId;
    private Integer ordinal;
    @Column(columnDefinition = "text")
    private String stdin;
    @Column(columnDefinition = "text")
    private String expectedOutput;
    private Boolean isSample;
    private BigDecimal points;
}
