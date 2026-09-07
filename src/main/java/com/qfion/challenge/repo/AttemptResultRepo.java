package com.qfion.challenge.repo;

import com.qfion.challenge.entity.AttemptResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttemptResultRepo extends JpaRepository<AttemptResult, Long> {
    List<AttemptResult> findByAttemptId(Long attemptId);
}
