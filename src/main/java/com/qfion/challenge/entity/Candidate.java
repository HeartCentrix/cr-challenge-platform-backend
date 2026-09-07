package com.qfion.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "candidate")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Candidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String emailRaw;
    private String emailNormalised;
    private String phoneRaw;
    private String phoneNormalised;
    private Boolean isConsented;
    private String sourceCampaign;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
}
