package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CandidateRepo extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByEmailNormalised(String emailNormalised);
    Optional<Candidate> findByPhoneNormalised(String phoneNormalised);
}
